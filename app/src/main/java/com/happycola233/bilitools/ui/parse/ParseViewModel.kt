package com.happycola233.bilitools.ui.parse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.NfoGenerator
import com.happycola233.bilitools.core.OpusAssetPlanner
import com.happycola233.bilitools.core.OpusMarkdownRenderer
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.core.naming.NamingContext
import com.happycola233.bilitools.core.naming.NamingContextFactory
import com.happycola233.bilitools.core.naming.NamingLabels
import com.happycola233.bilitools.core.naming.NamingRenderer
import com.happycola233.bilitools.core.naming.NamingShape
import com.happycola233.bilitools.core.naming.NamingStreamInfo
import com.happycola233.bilitools.core.naming.NamingTemplateScope
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.DefaultDownloadVideoCodec
import com.happycola233.bilitools.data.DownloadNamingSettings
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.MediaRepository
import com.happycola233.bilitools.data.InvalidMediaInputException
import com.happycola233.bilitools.data.OpusException
import com.happycola233.bilitools.data.OpusFailure
import com.happycola233.bilitools.data.OpusRepository
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.TopLevelFolderMode
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadExtraTaskOperation
import com.happycola233.bilitools.data.model.DownloadExtraTaskSpec
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.MediaCapabilities
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaSections
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.OpusDocument
import com.happycola233.bilitools.data.model.capabilities
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "ParseViewModel"

data class QualityOption(
    val id: Int,
    val label: String,
)

data class CodecOption(
    val codec: VideoCodec,
    val label: String,
)

data class AudioOption(
    val id: Int,
    val label: String,
)

data class ImageOption(
    val id: String,
    val label: String,
)

private data class ItemPresentationDetail(
    val stat: MediaStat?,
    val description: String?,
)

private data class StreamRequestKey(
    val mediaId: String,
    val mediaType: MediaType,
    val selectedSectionId: Long?,
    val pageIndex: Int,
    val collectionMode: Boolean,
    val selectedItemIndex: Int,
    val format: StreamFormat,
)

private data class ExtrasTargetKey(
    val mediaType: MediaType,
    val resourceId: String,
)

private fun MediaItem.extrasTargetKey(): ExtrasTargetKey {
    val resourceId = when {
        aid != null && cid != null -> "aid:$aid:cid:$cid"
        epid != null -> "epid:$epid"
        cid != null -> "cid:$cid"
        !bvid.isNullOrBlank() -> "bvid:$bvid:index:$index"
        cvid != null -> "cvid:$cvid"
        !opid.isNullOrBlank() -> "opid:$opid"
        else -> "url:$url:index:$index"
    }
    return ExtrasTargetKey(type, resourceId)
}

/** 下载列表里展示的分组标题，与写盘命名互不影响。 */
private data class DownloadGroupLabel(
    val title: String,
    val subtitle: String?,
)

/** 一次下载操作内共享的命名状态：设置快照、批次时间戳、已确定的顶层文件夹。 */
private data class NamingSession(
    val settings: DownloadNamingSettings,
    val downTimeEpochSeconds: Long,
    val useTopLevelFolder: Boolean = false,
    val topLevelFolderName: String? = null,
)

enum class QualityMode {
    Highest,
    Lowest,
    Fixed,
}

internal enum class SubtitleSelectionPolicy {
    SelectedLanguage,
    AllAvailable,
}

sealed interface SubtitleLanguageSelection {
    data object All : SubtitleLanguageSelection

    data class Language(val lan: String) : SubtitleLanguageSelection
}

internal fun subtitleSelectionPolicy(
    selectedItemCount: Int,
    languageSelection: SubtitleLanguageSelection?,
): SubtitleSelectionPolicy {
    return if (
        selectedItemCount > 1 ||
        languageSelection !is SubtitleLanguageSelection.Language
    ) {
        SubtitleSelectionPolicy.AllAvailable
    } else {
        SubtitleSelectionPolicy.SelectedLanguage
    }
}

internal fun selectSubtitles(
    subtitles: List<SubtitleInfo>,
    languageSelection: SubtitleLanguageSelection?,
    policy: SubtitleSelectionPolicy,
): List<SubtitleInfo> {
    return when (policy) {
        SubtitleSelectionPolicy.AllAvailable -> subtitles
        SubtitleSelectionPolicy.SelectedLanguage -> when (languageSelection) {
            SubtitleLanguageSelection.All -> subtitles
            is SubtitleLanguageSelection.Language -> subtitles
                .firstOrNull { it.lan == languageSelection.lan }
                ?.let(::listOf)
                .orEmpty()
            null -> emptyList()
        }
    }
}

internal fun pickSubtitleLanguageSelection(
    subtitles: List<SubtitleInfo>,
    currentSelection: SubtitleLanguageSelection?,
): SubtitleLanguageSelection? {
    if (subtitles.isEmpty()) return null
    if (subtitles.size == 1) return SubtitleLanguageSelection.Language(subtitles.first().lan)
    return when (currentSelection) {
        SubtitleLanguageSelection.All -> SubtitleLanguageSelection.All
        is SubtitleLanguageSelection.Language -> currentSelection
            .takeIf { selected -> subtitles.any { it.lan == selected.lan } }
            ?: SubtitleLanguageSelection.All
        null -> SubtitleLanguageSelection.All
    }
}

data class SubtitleCopyEntry(
    val title: String,
    val subtitleName: String?,
    val content: String?,
    val error: String? = null,
)

data class AiSummaryCopyEntry(
    val title: String,
    val content: String?,
    val error: String? = null,
)

sealed class ParseEvent {
    data class CopySingleSubtitle(val entry: SubtitleCopyEntry) : ParseEvent()
    data class ShowSubtitleCopyDialog(val entries: List<SubtitleCopyEntry>) : ParseEvent()
    data class CopySingleAiSummary(val entry: AiSummaryCopyEntry) : ParseEvent()
    data class ShowAiSummaryCopyDialog(val entries: List<AiSummaryCopyEntry>) : ParseEvent()
    data class DownloadQueued(val result: DownloadEnqueueResult) : ParseEvent()
}

data class DownloadEnqueueResult(
    val queuedGroups: Int,
    val failedItems: Int = 0,
)

internal data class DefaultParseContentSelection(
    val outputType: OutputType?,
    val opusContentEnabled: Boolean,
    val opusImagesEnabled: Boolean,
)

internal fun defaultParseContentSelection(type: MediaType?): DefaultParseContentSelection {
    val capabilities = type?.capabilities
    val supportsOpus = capabilities?.supportsOpusExport == true
    return DefaultParseContentSelection(
        outputType = OutputType.AudioVideo.takeIf { capabilities?.supportsPlaybackStream == true },
        opusContentEnabled = supportsOpus,
        opusImagesEnabled = supportsOpus,
    )
}

internal fun formatBiliApiErrorMessage(
    message: String?,
    code: Int,
    fallback: String,
): String {
    val detail = message
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "0" }
        ?: fallback
    return if (code == 0) detail else "$detail ($code)"
}

private data class PreparedDownloadTarget(
    val item: MediaItem,
    val opusDocument: OpusDocument?,
)

data class ParseUiState(
    val loading: Boolean = false,
    val streamLoading: Boolean = false,
    val collectionModeLoading: Boolean = false,
    val downloadStarting: Boolean = false,
    val subtitleCopying: Boolean = false,
    val aiSummaryCopying: Boolean = false,
    val error: String? = null,
    // 输入框本身的校验提示：就地展示在输入框下方，不占用顶部错误横幅。
    val inputError: String? = null,
    val notice: String? = null,
    val mediaInfo: MediaInfo? = null,
    val items: List<MediaItem> = emptyList(),
    val selectedItemIndex: Int = 0,
    val selectedItemIndices: List<Int> = emptyList(),
    val sections: MediaSections? = null,
    val selectedSectionId: Long? = null,
    val pageIndex: Int = 1,
    val collectionMode: Boolean = false,
    val selectedMediaType: MediaType? = null,
    val format: StreamFormat = StreamFormat.Dash,
    val outputType: OutputType? = OutputType.AudioVideo,
    val playUrlInfo: PlayUrlInfo? = null,
    val videoStreams: List<VideoStream> = emptyList(),
    val audioStreams: List<AudioStream> = emptyList(),
    val resolutions: List<QualityOption> = emptyList(),
    val codecs: List<CodecOption> = emptyList(),
    val audioBitrates: List<AudioOption> = emptyList(),
    val resolutionMode: QualityMode = QualityMode.Highest,
    val audioBitrateMode: QualityMode = QualityMode.Highest,
    val selectedResolutionId: Int? = null,
    val selectedCodec: VideoCodec? = null,
    val selectedAudioId: Int? = null,
    // 当前单选条目的语言选项；多选下载与复制始终逐条请求，不读取此缓存。
    val subtitleList: List<SubtitleInfo> = emptyList(),
    val subtitleLanguageSelection: SubtitleLanguageSelection? = null,
    val subtitleEnabled: Boolean = false,
    val aiSummaryAvailable: Boolean = false,
    val aiSummaryEnabled: Boolean = false,
    val nfoCollectionEnabled: Boolean = false,
    val nfoSingleEnabled: Boolean = false,
    val danmakuLiveEnabled: Boolean = false,
    val danmakuHistoryEnabled: Boolean = false,
    val danmakuDate: String = defaultDate(),
    val danmakuHour: String = "",
    val imageOptions: List<ImageOption> = emptyList(),
    val selectedImageIds: Set<String> = emptySet(),
    val opusContentEnabled: Boolean = false,
    val opusImagesEnabled: Boolean = false,
    val opusImagesAvailable: Boolean? = null,
    val warning: String? = null,
    // 行点击预览的条目索引：仅驱动信息卡片展示，不参与下载与导出参数的决策
    val previewItemIndex: Int? = null,
    val selectedItemStat: MediaStat? = null,
    val isLoggedIn: Boolean = false,
) {
    val hasSelectedDownloadContent: Boolean
        get() = outputType != null ||
            subtitleEnabled ||
            aiSummaryEnabled ||
            nfoCollectionEnabled ||
            nfoSingleEnabled ||
            danmakuLiveEnabled ||
            danmakuHistoryEnabled ||
            selectedImageIds.isNotEmpty() ||
            opusContentEnabled ||
            opusImagesEffectivelyEnabled

    val isMultiSelect: Boolean
        get() = selectedItemIndices.size > 1

    val opusImagesEffectivelyEnabled: Boolean
        get() = opusImagesEnabled && (isMultiSelect || opusImagesAvailable != false)
}

internal fun ParseUiState.canAutoLoadStream(): Boolean {
    return !loading &&
        !collectionModeLoading &&
        error.isNullOrBlank() &&
        mediaInfo != null &&
        outputType != null &&
        selectedItemIndices.isNotEmpty() &&
        items.getOrNull(selectedItemIndex)?.type?.capabilities?.supportsPlaybackStream == true
}

/**
 * 只保留当前媒体类型能够处理的附加选项。资源是否真实存在仍由详情探测决定；这里负责阻止
 * 图文等类型继承上一轮解析或多选状态中的播放器附属任务。
 */
internal fun ParseUiState.restrictExtraSelections(mediaTypes: Collection<MediaType>): ParseUiState {
    val capabilities = mediaTypes.map { type -> type.capabilities }
    return copy(
        subtitleEnabled = subtitleEnabled && capabilities.any { it.supportsSubtitleExport },
        aiSummaryEnabled = aiSummaryEnabled && capabilities.any { it.supportsAiSummaryExport },
        nfoCollectionEnabled = nfoCollectionEnabled && capabilities.any { it.supportsNfoExport },
        nfoSingleEnabled = nfoSingleEnabled && capabilities.any { it.supportsNfoExport },
        danmakuLiveEnabled = danmakuLiveEnabled && capabilities.any { it.supportsDanmakuExport },
        danmakuHistoryEnabled = danmakuHistoryEnabled && capabilities.any { it.supportsDanmakuExport },
        selectedImageIds = selectedImageIds.takeIf {
            capabilities.any { capability -> capability.supportsAuxiliaryImageExport }
        }.orEmpty(),
    )
}

/**
 * 单选且已确认没有正文图片时，禁用态勾选不能再当作有效选择。
 * 多选仍保留勾选，以便批次里其他条目继续导出图片。
 */
internal fun ParseUiState.withResolvedOpusImageSelection(): ParseUiState {
    if (opusImagesEnabled == opusImagesEffectivelyEnabled) return this
    return copy(opusImagesEnabled = false)
}

private fun ParseUiState.selectedMediaTypes(): List<MediaType> = selectedItemIndices
    .mapNotNull { index -> items.getOrNull(index)?.type }

private inline fun ParseUiState.selectedMediaSupports(
    capability: (MediaCapabilities) -> Boolean,
): Boolean = selectedMediaTypes().any { type -> capability(type.capabilities) }

private fun defaultDate(): String {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}

class ParseViewModel(
    private val mediaRepository: MediaRepository,
    private val opusRepository: OpusRepository,
    private val extrasRepository: ExtrasRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(applyDefaultDownloadQuality(ParseUiState()))
    val state: StateFlow<ParseUiState> = _state.asStateFlow()
    private val eventChannel = Channel<ParseEvent>(Channel.BUFFERED)
    val events: Flow<ParseEvent> = eventChannel.receiveAsFlow()
    private val fullResolutionIds = listOf(127, 126, 125, 120, 116, 112, 80, 64, 32, 16, 6)
    private val fullAudioIds = AudioQualities.allIds
    private val offsetMap = mutableMapOf<Int, String>()
    private val itemStatCache = mutableMapOf<String, MediaStat>()
    private val itemDescriptionCache = mutableMapOf<String, String>()
    private var streamLoadGeneration = 0L
    private var loadedStreamKey: StreamRequestKey? = null
    private var loadingStreamKey: StreamRequestKey? = null
    private var failedStreamKey: StreamRequestKey? = null
    private var extrasRefreshGeneration = 0L
    private var subtitleSelectionTargetKey: ExtrasTargetKey? = null

    init {
        refreshLoginState()
        viewModelScope.launch {
            _state
                .map { it.autoStreamRequestKeyOrNull() }
                .distinctUntilChanged()
                .collect { requestKey ->
                    if (requestKey == null) return@collect
                    if (
                        requestKey == loadedStreamKey ||
                        requestKey == loadingStreamKey ||
                        requestKey == failedStreamKey
                    ) {
                        return@collect
                    }
                    loadStream()
                }
        }
    }

    fun refreshLoginState() {
        viewModelScope.launch(Dispatchers.IO) {
            val loggedIn = authRepository.isLoggedIn()
            _state.update { it.copy(isLoggedIn = loggedIn) }
        }
    }

    fun setMediaType(type: MediaType?) {
        _state.update { it.copy(selectedMediaType = type) }
    }

    fun parse(input: String) {
        if (input.isBlank()) {
            _state.update {
                it.copy(inputError = strings.get(R.string.parse_error_empty_input), error = null)
            }
            return
        }
        invalidateExtrasRefresh()
        viewModelScope.launch {
            // 中止仍在进行的旧取流，但保留已完成/已失败标记。若本次解析失败，页面下方保留的
            // 旧结果便不会立刻触发重复取流并清掉刚产生的解析错误。
            invalidateActiveStreamLoad()
            _state.update {
                it.copy(
                    loading = true,
                    streamLoading = false,
                    collectionModeLoading = false,
                    error = null,
                    inputError = null,
                    notice = null,
                )
            }
            offsetMap.clear()
            itemStatCache.clear()
            itemDescriptionCache.clear()
            runCatching {
                val allowRaw = _state.value.selectedMediaType != null
                val parsed = mediaRepository.parseInput(input, allowRaw)
                val resolvedType =
                    _state.value.selectedMediaType ?: parsed.type ?: throw InvalidMediaInputException()
                val info = mediaRepository.getMediaInfo(
                    parsed.id,
                    resolvedType,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(target = parsed.target),
                )
                val defaultIndex =
                    info.list.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
                val defaultItem = info.list.getOrNull(defaultIndex)
                val defaultCapabilities = defaultItem?.type?.capabilities
                val defaultContentSelection = defaultParseContentSelection(defaultItem?.type)
                resetStreamLoadTracking()
                _state.update {
                    normalizeQualityModes(
                        applyDefaultDownloadQuality(
                            it.copy(
                                loading = false,
                                mediaInfo = info,
                                items = info.list,
                                selectedItemIndex = defaultIndex,
                                selectedItemIndices = if (info.list.isNotEmpty()) listOf(defaultIndex) else emptyList(),
                                sections = info.sections,
                                selectedSectionId = info.sections?.target,
                                pageIndex = 1,
                                collectionMode = false,
                                playUrlInfo = null,
                                videoStreams = emptyList(),
                                audioStreams = emptyList(),
                                resolutions = emptyList(),
                                codecs = emptyList(),
                                audioBitrates = emptyList(),
                                selectedResolutionId = null,
                                selectedCodec = null,
                                selectedAudioId = null,
                                format = StreamFormat.Dash,
                                outputType = defaultContentSelection.outputType,
                                opusContentEnabled = defaultContentSelection.opusContentEnabled,
                                opusImagesEnabled = defaultContentSelection.opusImagesEnabled,
                                opusImagesAvailable = null,
                                warning = null,
                                previewItemIndex = null,
                                selectedItemStat = info.list.getOrNull(defaultIndex)?.stat,
                                streamLoading = defaultCapabilities?.supportsPlaybackStream == true,
                                isLoggedIn = authRepository.isLoggedIn(),
                            ),
                        ).restrictExtraSelections(listOfNotNull(defaultItem?.type)),
                    )
                }
                info.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(info, item)
                }
                if (info.type == MediaType.UserOpus) {
                    offsetMap[1] = ""
                    val nextOffset = info.offset?.takeIf { info.hasMore != false }
                    if (nextOffset != null) {
                        offsetMap[2] = nextOffset
                    }
                }
            }.onFailure { err ->
                // 输入无法识别属于表单校验问题，就地提示比顶部横幅更贴近出错位置。
                if (err is InvalidMediaInputException) {
                    _state.update {
                        it.copy(
                            loading = false,
                            collectionModeLoading = false,
                            inputError = mapError(err),
                            isLoggedIn = authRepository.isLoggedIn(),
                        )
                    }
                } else {
                    setLoadingError(err)
                }
            }
        }
    }

    fun clear() {
        val selectedType = _state.value.selectedMediaType
        resetStreamLoadTracking()
        invalidateExtrasRefresh()
        offsetMap.clear()
        itemStatCache.clear()
        itemDescriptionCache.clear()
        _state.value = applyDefaultDownloadQuality(
            ParseUiState(
                selectedMediaType = selectedType,
                isLoggedIn = authRepository.isLoggedIn(),
            ),
        )
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearInputError() {
        if (_state.value.inputError == null) return
        _state.update { it.copy(inputError = null) }
    }

    fun toggleItemSelection(index: Int) {
        val items = _state.value.items
        if (index !in items.indices) return
        val selected = _state.value.selectedItemIndices.toMutableList()
        val previousSelectedCount = selected.size
        if (selected.contains(index)) {
            selected.remove(index)
        } else {
            selected.add(index)
        }
        selected.sort()
        val currentIndex = _state.value.selectedItemIndex
        val nextCurrent = if (selected.isNotEmpty() && !selected.contains(currentIndex)) {
            selected.first()
        } else {
            currentIndex
        }
        val currentChanged = nextCurrent != currentIndex
        val info = _state.value.mediaInfo
        val nextItem = info?.list?.getOrNull(nextCurrent)
        _state.update {
            val nextState = if (currentChanged) {
                it.copy(
                    selectedItemIndices = selected,
                    selectedItemIndex = nextCurrent,
                    selectedItemStat = nextItem?.stat,
                    opusImagesAvailable = null,
                    warning = streamWarningForPendingSelectionChange(it),
                )
            } else {
                it.copy(
                    selectedItemIndices = selected,
                    selectedItemIndex = nextCurrent,
                    selectedItemStat = info?.list?.getOrNull(nextCurrent)?.stat,
                )
            }
            normalizeQualityModes(nextState)
        }
        val returnedToSingleSelection = selected.size == 1 && previousSelectedCount != 1
        // 多选允许保留当前条目缺失的附加资源选项；退回单选时需重新按实际可用性收紧状态。
        if ((currentChanged || returnedToSingleSelection) && info != null && nextItem != null) {
            viewModelScope.launch {
                refreshExtras(info, nextItem)
            }
        }
    }

    // 行点击一律只做“预览”：仅切换信息卡片的展示内容，不移动当前项、不触发取流，
    // 下载与导出参数始终只由勾选集合决定（各列表类型与合集模式行为保持一致）。
    fun onItemRowClick(index: Int) {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        val rowClickable = info.type != MediaType.Video || (snapshot.collectionMode && info.collection)
        if (!rowClickable) return
        val item = snapshot.items.getOrNull(index) ?: return
        _state.update { it.copy(previewItemIndex = index) }
        refreshItemPresentation(info, item, index, fromPreview = true)
    }

    fun selectAllItems() {
        val items = _state.value.items
        if (items.isEmpty()) return
        val indices = items.indices.toList()
        val nextCurrent = if (_state.value.selectedItemIndex in items.indices) {
            _state.value.selectedItemIndex
        } else {
            indices.first()
        }
        _state.update {
            normalizeQualityModes(
                it.copy(
                    selectedItemIndices = indices,
                    selectedItemIndex = nextCurrent,
                    selectedItemStat = it.items.getOrNull(nextCurrent)?.stat,
                ),
            )
        }
    }

    fun clearSelectedItems() {
        _state.update {
            normalizeQualityModes(
                it.copy(
                    selectedItemIndices = emptyList(),
                    selectedItemStat = null,
                ),
            )
        }
    }

    fun loadPage(page: Int) {
        val info = _state.value.mediaInfo ?: return
        if (!info.paged) return
        val targetPage = page.coerceAtLeast(1)
        val targetSection = _state.value.selectedSectionId
        val collectionMode = _state.value.collectionMode
        val offset = if (info.type == MediaType.UserOpus) {
            offsetMap[targetPage] ?: run {
                _state.update { it.copy(notice = strings.get(R.string.parse_notice_page_not_loaded)) }
                return
            }
        } else {
            null
        }
        viewModelScope.launch {
            resetStreamLoadTracking()
            _state.update {
                it.copy(
                    loading = true,
                    streamLoading = false,
                    collectionModeLoading = false,
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                val updated = mediaRepository.getMediaInfo(
                    info.id,
                    info.type,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(
                        page = targetPage,
                        target = targetSection,
                        collection = collectionMode,
                        offset = offset,
                    ),
                )
                if (updated.list.isEmpty() && targetPage > 1) {
                    _state.update {
                        it.copy(
                            loading = false,
                            notice = strings.get(R.string.parse_notice_no_more),
                        )
                    }
                    return@launch
                }
                val defaultIndex =
                    updated.list.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
                _state.update {
                    normalizeQualityModes(
                        it.copy(
                            loading = false,
                            mediaInfo = updated,
                            items = updated.list,
                            selectedItemIndex = defaultIndex,
                            selectedItemIndices = if (updated.list.isNotEmpty()) listOf(defaultIndex) else emptyList(),
                            sections = updated.sections,
                            selectedSectionId = updated.sections?.target,
                            pageIndex = targetPage,
                            playUrlInfo = null,
                            videoStreams = emptyList(),
                            audioStreams = emptyList(),
                            resolutions = emptyList(),
                            codecs = emptyList(),
                            audioBitrates = emptyList(),
                            selectedResolutionId = null,
                            selectedCodec = null,
                            selectedAudioId = null,
                            warning = null,
                            previewItemIndex = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                            opusImagesAvailable = null,
                            streamLoading = updated.list.isNotEmpty() && it.outputType != null,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
                if (updated.type == MediaType.UserOpus) {
                    if (targetPage == 1) {
                        offsetMap[1] = ""
                    }
                    val nextOffset = updated.offset?.takeIf { updated.hasMore != false }
                    if (nextOffset != null) {
                        offsetMap[targetPage + 1] = nextOffset
                    } else {
                        offsetMap.remove(targetPage + 1)
                    }
                }
            }.onFailure { err ->
                setLoadingError(err)
            }
        }
    }

    fun loadNextPage() {
        loadPage(_state.value.pageIndex + 1)
    }

    fun loadPrevPage() {
        loadPage(_state.value.pageIndex - 1)
    }

    fun selectSection(sectionId: Long) {
        val info = _state.value.mediaInfo ?: return
        viewModelScope.launch {
            resetStreamLoadTracking()
            _state.update {
                it.copy(
                    loading = true,
                    streamLoading = false,
                    collectionModeLoading = false,
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                val updated = mediaRepository.getMediaInfo(
                    info.id,
                    info.type,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(
                        target = sectionId,
                        collection = _state.value.collectionMode,
                    ),
                )
                val defaultIndex =
                    updated.list.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
                _state.update {
                    normalizeQualityModes(
                        it.copy(
                            loading = false,
                            mediaInfo = updated,
                            items = updated.list,
                            selectedItemIndex = defaultIndex,
                            selectedItemIndices = if (updated.list.isNotEmpty()) listOf(defaultIndex) else emptyList(),
                            sections = updated.sections,
                            selectedSectionId = updated.sections?.target,
                            pageIndex = 1,
                            playUrlInfo = null,
                            videoStreams = emptyList(),
                            audioStreams = emptyList(),
                            resolutions = emptyList(),
                            codecs = emptyList(),
                            audioBitrates = emptyList(),
                            selectedResolutionId = null,
                            selectedCodec = null,
                            selectedAudioId = null,
                            warning = null,
                            previewItemIndex = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                            opusImagesAvailable = null,
                            streamLoading = updated.list.isNotEmpty() && it.outputType != null,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
            }.onFailure { err ->
                setLoadingError(err)
            }
        }
    }

    fun setCollectionMode(enabled: Boolean) {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (snapshot.collectionMode == enabled || snapshot.collectionModeLoading) return
        val previousMode = snapshot.collectionMode
        val target = snapshot.selectedSectionId
        viewModelScope.launch {
            resetStreamLoadTracking()
            _state.update {
                it.copy(
                    collectionMode = enabled,
                    collectionModeLoading = true,
                    streamLoading = false,
                    error = null,
                    notice = null,
                )
            }
            runCatching {
                val updated = mediaRepository.getMediaInfo(
                    info.id,
                    info.type,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(
                        target = target,
                        collection = enabled,
                    ),
                )
                val defaultIndex =
                    updated.list.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
                _state.update {
                    normalizeQualityModes(
                        it.copy(
                            loading = false,
                            mediaInfo = updated,
                            items = updated.list,
                            selectedItemIndex = defaultIndex,
                            selectedItemIndices = if (updated.list.isNotEmpty()) listOf(defaultIndex) else emptyList(),
                            sections = updated.sections,
                            selectedSectionId = updated.sections?.target,
                            collectionMode = enabled,
                            collectionModeLoading = false,
                            pageIndex = 1,
                            playUrlInfo = null,
                            videoStreams = emptyList(),
                            audioStreams = emptyList(),
                            resolutions = emptyList(),
                            codecs = emptyList(),
                            audioBitrates = emptyList(),
                            selectedResolutionId = null,
                            selectedCodec = null,
                            selectedAudioId = null,
                            warning = null,
                            previewItemIndex = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                            opusImagesAvailable = null,
                            streamLoading = updated.list.isNotEmpty() && it.outputType != null,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        collectionMode = previousMode,
                        collectionModeLoading = false,
                        streamLoading = false,
                        error = mapError(err),
                        isLoggedIn = authRepository.isLoggedIn(),
                    )
                }
            }
        }
    }

    fun setFormat(format: StreamFormat) {
        _state.update {
            val nextOutput = if (format == StreamFormat.Dash) {
                it.outputType
            } else {
                it.outputType?.let { OutputType.AudioVideo }
            }
            it.copy(
                format = format,
                outputType = nextOutput,
                warning = optimisticStreamWarningFor(format),
            )
        }
    }

    fun setOutputType(type: OutputType?) {
        _state.update { it.copy(outputType = type) }
    }

    fun setResolution(id: Int) {
        _state.update { current ->
            val nextMode = QualityMode.Fixed
            val nextCodecs = buildCodecOptionsForSelection(
                current.videoStreams,
                id,
                nextMode,
            )
            current.copy(
                resolutionMode = nextMode,
                selectedResolutionId = id,
                codecs = nextCodecs,
                selectedCodec = pickCodec(current.selectedCodec, nextCodecs),
            )
        }
    }

    fun setResolutionMode(mode: QualityMode) {
        _state.update { current ->
            val selectedCount = current.selectedItemIndices.size
            val nextResolutions = resolveResolutionOptions(current.videoStreams, mode, selectedCount)
            val nextResolutionId = pickResolutionId(current.selectedResolutionId, nextResolutions, mode)
            val nextCodecs = buildCodecOptionsForSelection(
                current.videoStreams,
                nextResolutionId,
                mode,
            )
            current.copy(
                resolutionMode = mode,
                resolutions = nextResolutions,
                selectedResolutionId = nextResolutionId,
                codecs = nextCodecs,
                selectedCodec = pickCodec(current.selectedCodec, nextCodecs),
            )
        }
    }

    fun setCodec(codec: VideoCodec) {
        _state.update { it.copy(selectedCodec = codec) }
    }

    fun setAudioBitrate(id: Int) {
        _state.update {
            it.copy(
                audioBitrateMode = QualityMode.Fixed,
                selectedAudioId = id,
            )
        }
    }

    fun setAudioBitrateMode(mode: QualityMode) {
        _state.update { current ->
            val selectedCount = current.selectedItemIndices.size
            val nextAudioOptions = resolveAudioOptions(current.audioStreams, mode, selectedCount)
            val nextAudioId = pickAudioId(current.selectedAudioId, nextAudioOptions, mode)
            current.copy(
                audioBitrateMode = mode,
                audioBitrates = nextAudioOptions,
                selectedAudioId = nextAudioId,
            )
        }
    }

    fun setSubtitleEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                subtitleEnabled = enabled && current.selectedMediaSupports {
                    it.supportsSubtitleExport
                },
            )
        }
    }

    fun setSubtitleLanguageSelection(selection: SubtitleLanguageSelection) {
        _state.update { it.copy(subtitleLanguageSelection = selection) }
    }

    fun setAiSummaryEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                aiSummaryEnabled = enabled && current.selectedMediaSupports {
                    it.supportsAiSummaryExport
                },
            )
        }
    }

    fun setNfoCollectionEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                nfoCollectionEnabled = enabled && current.selectedMediaSupports {
                    it.supportsNfoExport
                },
            )
        }
    }

    fun setNfoSingleEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                nfoSingleEnabled = enabled && current.selectedMediaSupports {
                    it.supportsNfoExport
                },
            )
        }
    }

    fun setDanmakuLiveEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                danmakuLiveEnabled = enabled && current.selectedMediaSupports {
                    it.supportsDanmakuExport
                },
            )
        }
    }

    fun setDanmakuHistoryEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                danmakuHistoryEnabled = enabled && current.selectedMediaSupports {
                    it.supportsDanmakuExport
                },
            )
        }
    }

    fun setDanmakuDate(value: String) {
        _state.update { it.copy(danmakuDate = value) }
    }

    fun setDanmakuHour(value: String) {
        _state.update { it.copy(danmakuHour = value) }
    }

    fun setImageSelection(id: String, selected: Boolean) {
        _state.update { current ->
            if (selected && !current.selectedMediaSupports { it.supportsAuxiliaryImageExport }) {
                return@update current
            }
            val updated = current.selectedImageIds.toMutableSet()
            if (selected) {
                updated.add(id)
            } else {
                updated.remove(id)
            }
            current.copy(selectedImageIds = updated)
        }
    }

    fun setOpusContentEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                opusContentEnabled = enabled && current.selectedMediaSupports {
                    it.supportsOpusExport
                },
            )
        }
    }

    fun setOpusImagesEnabled(enabled: Boolean) {
        _state.update { current ->
            when {
                enabled && !current.selectedMediaSupports { it.supportsOpusExport } -> current
                current.opusImagesAvailable == false && !current.isMultiSelect -> current
                else -> current.copy(opusImagesEnabled = enabled)
            }
        }
    }

    private fun loadStream() {
        if (_state.value.mediaInfo == null) return
        val item = currentItem() ?: return
        val requestKey = _state.value.streamRequestKeyOrNull() ?: return
        if (loadingStreamKey == requestKey) return
        loadingStreamKey = requestKey
        failedStreamKey = null
        val requestedFormat = _state.value.format
        val requestGeneration = ++streamLoadGeneration
        val itemIndex = _state.value.selectedItemIndex
        val info = _state.value.mediaInfo
        viewModelScope.launch {
            _state.update { it.copy(streamLoading = true, error = null, notice = null) }
            runCatching {
                val resolvedItem = mediaRepository.resolveItemForPlay(item, item.type)
                if (resolvedItem != item) {
                    _state.update { current ->
                        val updatedItems = current.items.toMutableList()
                        if (itemIndex in updatedItems.indices) {
                            updatedItems[itemIndex] = resolvedItem
                        }
                        current.copy(
                            items = updatedItems,
                            mediaInfo = current.mediaInfo?.copy(list = updatedItems),
                        )
                    }
                    info?.let { refreshExtras(it, resolvedItem) }
                }
                val playUrlInfo =
                    mediaRepository.getPlayUrlInfo(resolvedItem, resolvedItem.type, requestedFormat)
                val selectedCount = _state.value.selectedItemIndices.size
                val resolutions = resolveResolutionOptions(
                    playUrlInfo.video,
                    _state.value.resolutionMode,
                    selectedCount,
                )
                val audio = resolveAudioOptions(
                    playUrlInfo.audio,
                    _state.value.audioBitrateMode,
                    selectedCount,
                )
                val safeOutputType = when {
                    _state.value.outputType == null -> null
                    playUrlInfo.format != StreamFormat.Dash -> OutputType.AudioVideo
                    playUrlInfo.video.isEmpty() && playUrlInfo.audio.isNotEmpty() -> OutputType.AudioOnly
                    playUrlInfo.audio.isEmpty() && _state.value.outputType == OutputType.AudioOnly ->
                        OutputType.AudioVideo
                    else -> _state.value.outputType
                }
                val resolutionMode = _state.value.resolutionMode
                val selectedResolutionId = pickResolutionId(
                    _state.value.selectedResolutionId,
                    resolutions,
                    resolutionMode,
                )
                val codecs = buildCodecOptionsForSelection(
                    playUrlInfo.video,
                    selectedResolutionId,
                    resolutionMode,
                )
                val selectedCodec = pickCodec(_state.value.selectedCodec, codecs)
                val audioBitrateMode = _state.value.audioBitrateMode
                val selectedAudioId = pickAudioId(
                    _state.value.selectedAudioId,
                    audio,
                    audioBitrateMode,
                )
                _state.update { current ->
                    if (requestGeneration != streamLoadGeneration || loadingStreamKey != requestKey) {
                        return@update current
                    }
                    loadedStreamKey = requestKey
                    loadingStreamKey = null
                    current.copy(
                        streamLoading = false,
                        playUrlInfo = playUrlInfo,
                        videoStreams = playUrlInfo.video,
                        audioStreams = playUrlInfo.audio,
                        resolutions = resolutions,
                        codecs = codecs,
                        audioBitrates = audio,
                        outputType = safeOutputType,
                        selectedResolutionId = selectedResolutionId,
                        selectedCodec = selectedCodec,
                        selectedAudioId = selectedAudioId,
                        warning = streamWarningFor(requestedFormat, playUrlInfo.format),
                    )
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                if (requestGeneration == streamLoadGeneration) {
                    failedStreamKey = requestKey
                    loadingStreamKey = null
                    setStreamLoadingError(err)
                }
            }
        }
    }

    fun download() {
        val initialState = _state.value
        val info = initialState.mediaInfo ?: return
        val selectedIndices = initialState.selectedItemIndices.filter { it in initialState.items.indices }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
            return
        }
        val state = initialState.restrictExtraSelections(
            selectedIndices.map { index -> initialState.items[index].type },
        ).withResolvedOpusImageSelection()
        if (state != initialState) {
            _state.value = state
        }
        if (!state.hasSelectedDownloadContent) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_download_content)) }
            return
        }
        if (state.danmakuHistoryEnabled) {
            val historyInputError = when {
                !isValidDate(state.danmakuDate) -> strings.get(R.string.parse_error_invalid_date)
                state.danmakuHour.isNotBlank() && parseHour(state.danmakuHour) == null ->
                    strings.get(R.string.parse_error_invalid_hour)
                else -> null
            }
            if (historyInputError != null) {
                _state.update { it.copy(error = historyInputError) }
                return
            }
        }
        if (state.outputType != null && state.playUrlInfo == null) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_stream)) }
            return
        }
        val snapshot = state
        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadStarting = true,
                    error = null,
                    notice = null,
                )
            }
            val enqueueResult = runCatching {
                withContext(Dispatchers.IO) {
                    downloadRepository.ensureLoaded()
                    val targets = buildDownloadTargets(snapshot, info, selectedIndices)
                    val selectionPolicy = subtitleSelectionPolicy(
                        selectedItemCount = selectedIndices.size,
                        languageSelection = snapshot.subtitleLanguageSelection,
                    )
                    var queuedGroups = 0
                    var failedItems = 0
                    var firstFailure: Throwable? = null
                    val opusResults = resolveSelectedOpusDocuments(snapshot, targets)
                    val preparedTargets = targets.zip(opusResults).mapNotNull { (rawItem, result) ->
                        val error = result.exceptionOrNull()
                        if (error != null) {
                            failedItems += 1
                            if (firstFailure == null) firstFailure = error
                            AppLog.w(
                                TAG,
                                "[download] failed to resolve opus, title=${rawItem.title}",
                                error,
                            )
                            return@mapNotNull null
                        }
                        val document = result.getOrNull()?.withItemFallback(rawItem, rawItem.resolvedUpper(info))
                        PreparedDownloadTarget(
                            item = document?.let { rawItem.withOpusDocument(it) } ?: rawItem,
                            opusDocument = document,
                        )
                    }
                    if (preparedTargets.isEmpty()) {
                        return@withContext DownloadEnqueueResult(
                            queuedGroups = 0,
                            failedItems = failedItems,
                        ) to firstFailure
                    }
                    val namingSession = createNamingSession(
                        info = info,
                        items = preparedTargets.map(PreparedDownloadTarget::item),
                    )
                    preparedTargets.forEachIndexed { batchIndex, preparedTarget ->
                        val batchOrdinal = batchIndex + 1
                        val opusDocument = preparedTarget.opusDocument
                        val preparedItem = preparedTarget.item
                        val item = runCatching { mediaRepository.resolveItemForPlay(preparedItem, preparedItem.type) }
                            .getOrDefault(preparedItem)
                        val playUrlResult = if (snapshot.outputType != null) {
                            runCatching {
                                mediaRepository.getPlayUrlInfo(item, item.type, snapshot.format)
                            }
                        } else {
                            null
                        }
                        val playUrlInfo = playUrlResult?.getOrNull()
                        val groupLabel = resolveGroupLabel(info = info, item = item)

                        val requestedGroupRelativePath = buildRequestedGroupRelativePath(
                            info = info,
                            item = item,
                            namingSession = namingSession,
                            batchOrdinal = batchOrdinal,
                        )

                        val groupId = downloadRepository.createGroup(
                            groupLabel.title,
                            groupLabel.subtitle,
                            item.displayContentId(),
                            item.coverUrl,
                            relativePath = requestedGroupRelativePath,
                        )
                        val trackTotal = when {
                            (item.pageCount ?: 0) > 0 -> item.pageCount
                            !info.paged && info.list.size > 1 -> info.list.size
                            else -> null
                        }
                        val embeddedMetadata = buildEmbeddedMetadata(
                            info = info,
                            item = item,
                            fallbackAlbum = item.workTitle?.takeIf { it.isNotBlank() }
                                ?: groupLabel.title,
                            trackTotal = trackTotal,
                        )

                        val outputType = snapshot.outputType
                        if (outputType != null && playUrlInfo == null) {
                            val streamError = playUrlResult?.exceptionOrNull()
                            val message = streamError?.let(::mapError)
                                ?: strings.get(R.string.parse_error_no_stream)
                            AppLog.w(
                                TAG,
                                "[download] failed to resolve stream, type=${item.type}, title=${item.title}",
                                streamError,
                            )
                            _state.update { it.copy(error = message) }
                        }
                        if (outputType != null && playUrlInfo != null) {
                            val selectedVideo = selectVideoStream(
                                playUrlInfo.video,
                                snapshot.selectedResolutionId,
                                snapshot.selectedCodec,
                                snapshot.resolutionMode,
                            )
                            val mergeVideo = if (playUrlInfo.format == StreamFormat.Dash &&
                                outputType == OutputType.AudioVideo) {
                                selectVideoStreamForMerge(
                                    playUrlInfo.video,
                                    selectedVideo,
                                )
                            } else {
                                selectedVideo
                            }
                            val selectedAudio = selectAudioStream(
                                playUrlInfo.audio,
                                snapshot.selectedAudioId,
                                snapshot.audioBitrateMode,
                            )
                            val downloadTitle = when (outputType) {
                                OutputType.AudioOnly -> strings.get(R.string.output_audio)
                                OutputType.VideoOnly -> strings.get(R.string.output_video)
                                OutputType.AudioVideo -> strings.get(R.string.output_audio_video)
                            }
                            val downloadTaskType = when (outputType) {
                                OutputType.AudioOnly -> DownloadTaskType.Audio
                                OutputType.VideoOnly -> DownloadTaskType.Video
                                OutputType.AudioVideo -> DownloadTaskType.AudioVideo
                            }
                            val unavailableReason = when (outputType) {
                                OutputType.AudioOnly -> if (selectedAudio == null) {
                                    strings.get(R.string.download_unavailable_audio)
                                } else {
                                    null
                                }
                                OutputType.VideoOnly -> if (selectedVideo == null) {
                                    strings.get(R.string.download_unavailable_video)
                                } else {
                                    null
                                }
                                OutputType.AudioVideo -> when {
                                    mergeVideo == null -> strings.get(R.string.download_unavailable_video)
                                    playUrlInfo.format == StreamFormat.Dash && selectedAudio == null ->
                                        strings.get(R.string.download_unavailable_audio_video)
                                    else -> null
                                }
                            }
                            if (unavailableReason == null) {
                                val outputVideoCodec = when (outputType) {
                                    OutputType.AudioOnly -> null
                                    OutputType.VideoOnly -> selectedVideo?.codec ?: snapshot.selectedCodec
                                    OutputType.AudioVideo -> mergeVideo?.codec ?: selectedVideo?.codec ?: snapshot.selectedCodec
                                }
                                when (outputType) {
                                    OutputType.AudioOnly -> {
                                        val mediaParams = buildMediaParams(null, null, selectedAudio)
                                        val audioExtension = extensionForAudioStream(selectedAudio!!)
                                        val audioNamingContext = buildNamingContext(
                                            info = info,
                                            item = item,
                                            namingSession = namingSession,
                                            batchIndex = batchOrdinal,
                                            taskType = DownloadTaskType.Audio,
                                            taskLabel = downloadTitle,
                                            mediaParams = mediaParams,
                                            formatLabel = mapOutputExtensionLabel(audioExtension),
                                        )
                                        val audioName = resolveTemplateFileName(
                                            item = item,
                                            namingSession = namingSession,
                                            context = audioNamingContext,
                                            extension = audioExtension,
                                        )
                                        downloadRepository.enqueue(
                                            groupId,
                                            DownloadTaskType.Audio,
                                            downloadTitle,
                                            audioName,
                                            selectedAudio.url,
                                            mediaParams,
                                            embeddedMetadata = embeddedMetadata,
                                        )
                                    }
                                    OutputType.VideoOnly -> {
                                        val mediaParams = buildMediaParams(selectedVideo, outputVideoCodec, null)
                                        val videoNamingContext = buildNamingContext(
                                            info = info,
                                            item = item,
                                            namingSession = namingSession,
                                            batchIndex = batchOrdinal,
                                            taskType = DownloadTaskType.Video,
                                            taskLabel = downloadTitle,
                                            mediaParams = mediaParams,
                                            formatLabel = mapStreamFormatLabel(selectedVideo!!.format),
                                        )
                                        val videoName = resolveTemplateFileName(
                                            item = item,
                                            namingSession = namingSession,
                                            context = videoNamingContext,
                                            extension = extensionForVideoStream(selectedVideo),
                                        )
                                        downloadRepository.enqueue(
                                            groupId,
                                            DownloadTaskType.Video,
                                            downloadTitle,
                                            videoName,
                                            selectedVideo.url,
                                            mediaParams,
                                            embeddedMetadata = embeddedMetadata,
                                        )
                                    }
                                    OutputType.AudioVideo -> {
                                        if (playUrlInfo.format == StreamFormat.Dash && selectedAudio != null) {
                                            val mediaParams = buildMediaParams(mergeVideo, outputVideoCodec, selectedAudio)
                                            val mergedExtension = extensionForMergedOutput(selectedAudio)
                                            val mergedNamingContext = buildNamingContext(
                                                info = info,
                                                item = item,
                                                namingSession = namingSession,
                                                batchIndex = batchOrdinal,
                                                taskType = DownloadTaskType.AudioVideo,
                                                taskLabel = downloadTitle,
                                                mediaParams = mediaParams,
                                                formatLabel = mapOutputExtensionLabel(mergedExtension),
                                            )
                                            val outputName = resolveTemplateFileName(
                                                item = item,
                                                namingSession = namingSession,
                                                context = mergedNamingContext,
                                                extension = mergedExtension,
                                            )
                                            downloadRepository.enqueueDashMerge(
                                                groupId,
                                                downloadTitle,
                                                outputName,
                                                mergeVideo!!.url,
                                                selectedAudio.url,
                                                mediaParams,
                                                embeddedMetadata = embeddedMetadata,
                                            )
                                        } else {
                                            val mediaParams = buildMediaParams(mergeVideo, outputVideoCodec, selectedAudio)
                                            val mergedNamingContext = buildNamingContext(
                                                info = info,
                                                item = item,
                                                namingSession = namingSession,
                                                batchIndex = batchOrdinal,
                                                taskType = DownloadTaskType.AudioVideo,
                                                taskLabel = downloadTitle,
                                                mediaParams = mediaParams,
                                                formatLabel = mapStreamFormatLabel(mergeVideo!!.format),
                                            )
                                            val videoName = resolveTemplateFileName(
                                                item = item,
                                                namingSession = namingSession,
                                                context = mergedNamingContext,
                                                extension = extensionForVideoStream(mergeVideo),
                                            )
                                            downloadRepository.enqueue(
                                                groupId,
                                                DownloadTaskType.AudioVideo,
                                                downloadTitle,
                                                videoName,
                                                mergeVideo.url,
                                                mediaParams,
                                                embeddedMetadata = embeddedMetadata,
                                            )
                                        }
                                    }
                                }
                            } else {
                                downloadRepository.addUnavailableTask(
                                    groupId = groupId,
                                    type = downloadTaskType,
                                    taskTitle = downloadTitle,
                                    reason = unavailableReason,
                                )
                            }
                        }

                        if (opusDocument != null) {
                            enqueueOpusTasks(
                                snapshot = snapshot,
                                info = info,
                                item = item,
                                document = opusDocument,
                                namingSession = namingSession,
                                batchOrdinal = batchOrdinal,
                                groupId = groupId,
                            )
                        }

                        launchExtraTasksForItem(
                            snapshot = snapshot,
                            info = info,
                            item = item,
                            namingSession = namingSession,
                            batchOrdinal = batchOrdinal,
                            groupId = groupId,
                            subtitleSelectionPolicy = selectionPolicy,
                        )
                        queuedGroups += 1
                    }
                    DownloadEnqueueResult(queuedGroups = queuedGroups, failedItems = failedItems) to firstFailure
                }
            }
            enqueueResult.fold(
                onSuccess = { (result, firstFailure) ->
                    if (result.queuedGroups > 0) {
                        val notice = if (result.failedItems > 0) {
                            strings.get(R.string.parse_notice_download_started_partial, result.failedItems)
                        } else {
                            strings.get(R.string.parse_notice_download_started)
                        }
                        _state.update { it.copy(downloadStarting = false, notice = notice, error = null) }
                        eventChannel.send(ParseEvent.DownloadQueued(result))
                    } else {
                        _state.update {
                            it.copy(
                                downloadStarting = false,
                                error = firstFailure?.let(::mapError)
                                    ?: strings.get(R.string.parse_error_failed),
                            )
                        }
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _state.update { it.copy(downloadStarting = false, error = mapError(error)) }
                },
            )
        }
    }

    private suspend fun resolveSelectedOpusDocuments(
        snapshot: ParseUiState,
        items: List<MediaItem>,
    ): List<Result<OpusDocument?>> = coroutineScope {
        val requestSemaphore = Semaphore(OPUS_DETAIL_DOWNLOAD_PARALLELISM)
        items.map { item ->
            async {
                val requested = item.type.capabilities.supportsOpusExport &&
                    (snapshot.opusContentEnabled || snapshot.opusImagesEffectivelyEnabled)
                if (!requested) return@async Result.success(null)
                try {
                    Result.success(requestSemaphore.withPermit { opusRepository.getDocument(item) })
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
            }
        }.awaitAll()
    }

    private fun MediaItem.withOpusDocument(document: OpusDocument): MediaItem = copy(
        title = document.title,
        coverUrl = document.images.firstOrNull()?.url ?: coverUrl,
        description = document.summary,
        stat = document.stat,
        url = document.sourceUrl,
        pubTime = document.publishedAt ?: pubTime,
        opid = document.id,
        cvid = document.cvid ?: cvid,
    )

    private fun OpusDocument.withItemFallback(
        item: MediaItem,
        fallbackAuthor: com.happycola233.bilitools.data.model.MediaUpper?,
    ): OpusDocument = copy(
        cvid = cvid ?: item.cvid,
        author = author?.takeIf { it.name.isNotBlank() } ?: fallbackAuthor ?: author,
        publishedAt = publishedAt ?: item.pubTime.takeIf { it > 0L },
        stat = MediaStat(
            play = stat.play ?: item.stat?.play,
            danmaku = stat.danmaku ?: item.stat?.danmaku,
            reply = stat.reply ?: item.stat?.reply,
            like = stat.like ?: item.stat?.like,
            coin = stat.coin ?: item.stat?.coin,
            favorite = stat.favorite ?: item.stat?.favorite,
            share = stat.share ?: item.stat?.share,
        ),
    )

    /**
     * 先一次性确定 Markdown 与全部原图文件名，再注册任务。这样正文中的相对链接与
     * MediaStore 中的最终目标始终使用同一份不可变资产清单。
     */
    private fun enqueueOpusTasks(
        snapshot: ParseUiState,
        info: MediaInfo,
        item: MediaItem,
        document: OpusDocument,
        namingSession: NamingSession,
        batchOrdinal: Int,
        groupId: Long,
    ) {
        val contentTitle = strings.get(R.string.parse_opus_content)
        val imageTitle = strings.get(R.string.parse_opus_images)
        // 图片序号进模板，模板里写不写 {img} 都由 OpusAssetPlanner 保证文件名不撞。
        val imageAssets = OpusAssetPlanner.plan(document) { index, total ->
            resolveTemplateBaseName(
                item = item,
                namingSession = namingSession,
                context = buildNamingContext(
                    info = info,
                    item = item,
                    namingSession = namingSession,
                    batchIndex = batchOrdinal,
                    taskType = DownloadTaskType.OpusImage,
                    taskLabel = imageTitle,
                    imageOrdinal = OpusAssetPlanner.imageOrdinal(index, total),
                ),
            )
        }

        if (snapshot.opusContentEnabled) {
            val contentContext = buildNamingContext(
                info = info,
                item = item,
                namingSession = namingSession,
                batchIndex = batchOrdinal,
                taskType = DownloadTaskType.OpusContent,
                taskLabel = contentTitle,
            )
            val contentName = resolveTemplateFileName(
                item = item,
                namingSession = namingSession,
                context = contentContext,
                extension = "md",
            )
            val localAssets = if (snapshot.opusImagesEffectivelyEnabled) imageAssets else emptyList()
            downloadRepository.enqueueExtraTask(
                groupId = groupId,
                type = DownloadTaskType.OpusContent,
                taskTitle = contentTitle,
                fileName = contentName,
                spec = DownloadExtraTaskSpec(
                    operation = DownloadExtraTaskOperation.StaticText,
                    mimeType = "text/markdown",
                    unavailableMessage = strings.get(R.string.parse_error_opus_invalid_response),
                    textContent = OpusMarkdownRenderer.render(document, localAssets),
                ),
            )
        }

        if (snapshot.opusImagesEffectivelyEnabled) {
            if (imageAssets.isEmpty()) {
                // 单选时选项已被禁用，不再留下一条“资源不可用”记录。
                if (snapshot.isMultiSelect) {
                    downloadRepository.addUnavailableTask(
                        groupId = groupId,
                        type = DownloadTaskType.OpusImage,
                        taskTitle = imageTitle,
                        reason = strings.get(R.string.download_unavailable_opus_images),
                    )
                }
            } else {
                imageAssets.forEachIndexed { index, asset ->
                    downloadRepository.enqueue(
                        groupId = groupId,
                        type = DownloadTaskType.OpusImage,
                        taskTitle = "$imageTitle ${index + 1}/${imageAssets.size}",
                        fileName = asset.fileName,
                        url = asset.image.url,
                    )
                }
            }
        }
    }

    private fun launchExtraTasksForItem(
        snapshot: ParseUiState,
        info: MediaInfo,
        item: MediaItem,
        namingSession: NamingSession,
        batchOrdinal: Int,
        groupId: Long,
        subtitleSelectionPolicy: SubtitleSelectionPolicy,
    ) {
        val capabilities = item.type.capabilities

        if (snapshot.subtitleEnabled && capabilities.supportsSubtitleExport) {
            val aid = item.aid
            val cid = item.cid
            val subtitleTitle = strings.get(R.string.parse_subtitle_label)
            if (aid == null || cid == null) {
                addUnavailableExtraTask(
                    groupId,
                    DownloadTaskType.Subtitle,
                    subtitleTitle,
                    strings.get(R.string.parse_error_no_subtitle),
                )
            } else {
                val subtitleContext = buildNamingContext(
                    info = info,
                    item = item,
                    namingSession = namingSession,
                    batchIndex = batchOrdinal,
                    taskType = DownloadTaskType.Subtitle,
                    taskLabel = subtitleTitle,
                    mediaParams = null,
                )
                val initialName = resolveTemplateFileName(
                    item = item,
                    namingSession = namingSession,
                    context = subtitleContext,
                    extension = if (subtitleSelectionPolicy == SubtitleSelectionPolicy.SelectedLanguage) {
                        (snapshot.subtitleLanguageSelection as? SubtitleLanguageSelection.Language)
                            ?.lan
                            ?.let { "$it.srt" }
                            ?: "srt"
                    } else {
                        "srt"
                    },
                )
                val subtitleBaseFileName = resolveTemplateBaseName(
                    item = item,
                    namingSession = namingSession,
                    context = subtitleContext,
                )
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = DownloadTaskType.Subtitle,
                    taskTitle = subtitleTitle,
                    fileName = initialName,
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.SubtitleDiscovery,
                        unavailableMessage = strings.get(R.string.parse_error_no_subtitle),
                        aid = aid,
                        cid = cid,
                        downloadAllSubtitles =
                            subtitleSelectionPolicy == SubtitleSelectionPolicy.AllAvailable,
                        selectedSubtitleLanguage =
                            (snapshot.subtitleLanguageSelection as? SubtitleLanguageSelection.Language)?.lan,
                        subtitleBaseFileName = subtitleBaseFileName,
                        subtitleTaskTitle = subtitleTitle,
                        cleanFileNameSeparators = namingSession.settings.cleanSeparators,
                    ),
                )
            }
        }

        if (snapshot.aiSummaryEnabled && capabilities.supportsAiSummaryExport) {
            val aid = item.aid
            val cid = item.cid
            val bvid = item.bvid
            val taskTitle = strings.get(R.string.parse_ai_summary_label)
            if (aid == null || cid == null || bvid.isNullOrBlank()) {
                addUnavailableExtraTask(
                    groupId,
                    DownloadTaskType.AiSummary,
                    taskTitle,
                    strings.get(R.string.parse_error_no_ai),
                )
            } else {
                val summaryTitle = info.nfo.showTitle?.ifBlank { item.title } ?: item.title
                val aiSummaryContext = buildNamingContext(
                    info = info,
                    item = item,
                    namingSession = namingSession,
                    batchIndex = batchOrdinal,
                    taskType = DownloadTaskType.AiSummary,
                    taskLabel = taskTitle,
                    mediaParams = null,
                )
                val name = resolveTemplateFileName(
                    item = item,
                    namingSession = namingSession,
                    context = aiSummaryContext,
                    extension = "md",
                )
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = DownloadTaskType.AiSummary,
                    taskTitle = taskTitle,
                    fileName = name,
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.AiSummary,
                        unavailableMessage = strings.get(R.string.parse_error_no_ai),
                        summaryTitle = summaryTitle,
                        bvid = bvid,
                        aid = aid,
                        cid = cid,
                    ),
                )
            }
        }

        if (snapshot.nfoCollectionEnabled && capabilities.supportsNfoExport) {
            val taskTitle = strings.get(R.string.parse_nfo_collection)
            if (isCollectionNfoAvailable(info)) {
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = DownloadTaskType.NfoCollection,
                    taskTitle = taskTitle,
                    fileName = "tvshow.nfo",
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.StaticText,
                        unavailableMessage = strings.get(R.string.parse_error_no_nfo),
                        textContent = NfoGenerator.buildCollectionNfo(info),
                    ),
                )
            } else {
                addUnavailableExtraTask(
                    groupId,
                    DownloadTaskType.NfoCollection,
                    taskTitle,
                    strings.get(R.string.parse_error_no_nfo),
                )
            }
        }

        if (snapshot.nfoSingleEnabled && capabilities.supportsNfoExport) {
            val taskTitle = strings.get(R.string.parse_nfo_single)
            val nfoContext = buildNamingContext(
                info = info,
                item = item,
                namingSession = namingSession,
                batchIndex = batchOrdinal,
                taskType = DownloadTaskType.NfoSingle,
                taskLabel = taskTitle,
                mediaParams = null,
            )
            val name = resolveTemplateFileName(
                item = item,
                namingSession = namingSession,
                context = nfoContext,
                extension = "nfo",
            )
            downloadRepository.enqueueExtraTask(
                groupId = groupId,
                type = DownloadTaskType.NfoSingle,
                taskTitle = taskTitle,
                fileName = name,
                spec = DownloadExtraTaskSpec(
                    operation = DownloadExtraTaskOperation.StaticText,
                    unavailableMessage = strings.get(R.string.parse_error_no_nfo),
                    textContent = NfoGenerator.buildSingleNfo(info, item),
                ),
            )
        }

        val convertXmlDanmakuToAss = settingsRepository.shouldConvertXmlDanmakuToAss()
        if (snapshot.danmakuLiveEnabled && capabilities.supportsDanmakuExport) {
            val aid = item.aid
            val cid = item.cid
            val duration = item.duration
            val taskTitle = strings.get(R.string.parse_danmaku_live)
            val danmakuLiveContext = buildNamingContext(
                info = info,
                item = item,
                namingSession = namingSession,
                batchIndex = batchOrdinal,
                taskType = DownloadTaskType.DanmakuLive,
                taskLabel = taskTitle,
                mediaParams = null,
            )
            val name = resolveTemplateFileName(
                item = item,
                namingSession = namingSession,
                context = danmakuLiveContext,
                extension = if (convertXmlDanmakuToAss) "ass" else "xml",
            )
            if (aid == null || cid == null) {
                addUnavailableExtraTask(
                    groupId,
                    DownloadTaskType.DanmakuLive,
                    taskTitle,
                    strings.get(R.string.parse_error_no_danmaku),
                )
            } else {
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = DownloadTaskType.DanmakuLive,
                    taskTitle = taskTitle,
                    fileName = name,
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.DanmakuLive,
                        unavailableMessage = strings.get(R.string.parse_error_no_danmaku),
                        aid = aid,
                        cid = cid,
                        durationSeconds = duration,
                        convertDanmakuToAss = convertXmlDanmakuToAss,
                    ),
                )
            }
        }

        if (snapshot.danmakuHistoryEnabled && capabilities.supportsDanmakuExport) {
            val date = snapshot.danmakuDate
            val hour = parseHour(snapshot.danmakuHour)
            val taskTitle = strings.get(R.string.parse_danmaku_history)
            val cid = item.cid
            if (cid == null) {
                addUnavailableExtraTask(
                    groupId,
                    DownloadTaskType.DanmakuHistory,
                    taskTitle,
                    strings.get(R.string.parse_error_no_danmaku),
                )
            } else {
                val danmakuHistoryContext = buildNamingContext(
                    info = info,
                    item = item,
                    namingSession = namingSession,
                    batchIndex = batchOrdinal,
                    taskType = DownloadTaskType.DanmakuHistory,
                    taskLabel = taskTitle,
                    mediaParams = null,
                )
                val name = resolveTemplateFileName(
                    item = item,
                    namingSession = namingSession,
                    context = danmakuHistoryContext,
                    extension = if (convertXmlDanmakuToAss) "ass" else "xml",
                )
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = DownloadTaskType.DanmakuHistory,
                    taskTitle = taskTitle,
                    fileName = name,
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.DanmakuHistory,
                        unavailableMessage = strings.get(R.string.parse_error_no_danmaku),
                        cid = cid,
                        date = date,
                        hour = hour,
                        convertDanmakuToAss = convertXmlDanmakuToAss,
                    ),
                )
            }
        }

        val selectedIds = snapshot.selectedImageIds.takeIf {
            capabilities.supportsAuxiliaryImageExport
        }.orEmpty()
        if (selectedIds.isNotEmpty()) {
            val thumbs = info.nfo.thumbs
                .filter { it.url.isNotBlank() }
                .filter { selectedIds.contains(it.id) }
                .distinctBy { it.id }
            val labelCounts = thumbs.groupingBy { thumb ->
                mapImageLabel(thumb.id)
            }.eachCount()
            thumbs.forEach { thumb ->
                val label = mapImageLabel(thumb.id)
                val fileLabel = if ((labelCounts[label] ?: 0) > 1) {
                    "$label-${thumb.id}"
                } else {
                    label
                }
                val taskType = imageDownloadTaskType(thumb.id)
                val imageContext = buildNamingContext(
                    info = info,
                    item = item,
                    namingSession = namingSession,
                    taskType = taskType,
                    taskLabel = fileLabel,
                    mediaParams = null,
                )
                val name = resolveTemplateFileName(
                    item = item,
                    namingSession = namingSession,
                    context = imageContext,
                    extension = extensionFromUrl(thumb.url),
                )
                downloadRepository.enqueueExtraTask(
                    groupId = groupId,
                    type = taskType,
                    taskTitle = label,
                    fileName = name,
                    spec = DownloadExtraTaskSpec(
                        operation = DownloadExtraTaskOperation.FetchBytes,
                        mimeType = "image/*",
                        unavailableMessage = strings.get(R.string.download_unavailable_image),
                        sourceUrl = thumb.url,
                    ),
                )
            }
            val availableIds = thumbs.mapTo(mutableSetOf()) { it.id }
            selectedIds
                .filterNot(availableIds::contains)
                .sorted()
                .forEach { unavailableId ->
                    val label = mapImageLabel(unavailableId)
                    addUnavailableExtraTask(
                        groupId = groupId,
                        type = imageDownloadTaskType(unavailableId),
                        taskTitle = label,
                        reason = strings.get(R.string.download_unavailable_image),
                    )
                }
        }
    }

    fun copySubtitlesNow() {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (snapshot.subtitleCopying || snapshot.aiSummaryCopying) return
        val requestedIndices = snapshot.selectedItemIndices.filter { it in snapshot.items.indices }
        if (requestedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
            return
        }
        val selectedIndices = requestedIndices.filter { index ->
            snapshot.items[index].type.capabilities.supportsSubtitleExport
        }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_subtitle)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    subtitleCopying = true,
                    error = null,
                    notice = null,
                )
            }
            val entries = runCatching {
                withContext(Dispatchers.IO) {
                    buildSubtitleCopyEntries(snapshot, info, selectedIndices)
                }
            }.getOrElse { err ->
                val message = mapError(err)
                _state.update {
                    it.copy(
                        subtitleCopying = false,
                        error = message,
                    )
                }
                return@launch
            }
            val availableCount = entries.count { !it.content.isNullOrBlank() }
            if (availableCount <= 0) {
                val message = strings.get(R.string.parse_error_no_subtitle)
                _state.update {
                    it.copy(
                        subtitleCopying = false,
                        error = message,
                    )
                }
                return@launch
            }
            _state.update { it.copy(subtitleCopying = false) }
            if (entries.size <= 1) {
                val entry = entries.firstOrNull()
                if (entry?.content.isNullOrBlank()) {
                    val message = strings.get(R.string.parse_error_no_subtitle)
                    _state.update {
                        it.copy(
                            error = message,
                        )
                    }
                    return@launch
                }
                eventChannel.send(ParseEvent.CopySingleSubtitle(entry))
                return@launch
            }
            eventChannel.send(ParseEvent.ShowSubtitleCopyDialog(entries))
        }
    }

    fun copyAiSummariesNow() {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (snapshot.subtitleCopying || snapshot.aiSummaryCopying) return
        val requestedIndices = snapshot.selectedItemIndices.filter { it in snapshot.items.indices }
        if (requestedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
            return
        }
        val selectedIndices = requestedIndices.filter { index ->
            snapshot.items[index].type.capabilities.supportsAiSummaryExport
        }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_ai)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiSummaryCopying = true,
                    error = null,
                    notice = null,
                )
            }
            val entries = runCatching {
                withContext(Dispatchers.IO) {
                    buildAiSummaryCopyEntries(snapshot, info, selectedIndices)
                }
            }.getOrElse { err ->
                val message = mapError(err)
                _state.update {
                    it.copy(
                        aiSummaryCopying = false,
                        error = message,
                    )
                }
                return@launch
            }
            val availableCount = entries.count { !it.content.isNullOrBlank() }
            if (availableCount <= 0) {
                val message = strings.get(R.string.parse_error_no_ai)
                _state.update {
                    it.copy(
                        aiSummaryCopying = false,
                        error = message,
                    )
                }
                return@launch
            }
            _state.update { it.copy(aiSummaryCopying = false) }
            if (entries.size <= 1) {
                val entry = entries.firstOrNull()
                if (entry?.content.isNullOrBlank()) {
                    val message = strings.get(R.string.parse_error_no_ai)
                    _state.update {
                        it.copy(
                            error = message,
                        )
                    }
                    return@launch
                }
                eventChannel.send(ParseEvent.CopySingleAiSummary(entry))
                return@launch
            }
            eventChannel.send(ParseEvent.ShowAiSummaryCopyDialog(entries))
        }
    }

    private suspend fun buildSubtitleCopyEntries(
        snapshot: ParseUiState,
        info: MediaInfo,
        selectedIndices: List<Int>,
    ): List<SubtitleCopyEntry> {
        val targets = buildDownloadTargets(snapshot, info, selectedIndices)
        val selectionPolicy = subtitleSelectionPolicy(
            selectedItemCount = selectedIndices.size,
            languageSelection = snapshot.subtitleLanguageSelection,
        )
        return targets.flatMap { rawItem ->
            val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                .getOrDefault(rawItem)
            val groupLabel = resolveGroupLabel(info = info, item = item)
            val aid = item.aid
            val cid = item.cid
            val subtitles = if (aid != null && cid != null) {
                runCatching { extrasRepository.getSubtitles(aid, cid) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val selectedSubtitles = selectSubtitles(
                subtitles = subtitles,
                languageSelection = snapshot.subtitleLanguageSelection,
                policy = selectionPolicy,
            )
            val title = buildSubtitleEntryTitle(groupLabel.title, groupLabel.subtitle)
            if (selectedSubtitles.isEmpty()) {
                listOf(
                    SubtitleCopyEntry(
                        title = title,
                        subtitleName = null,
                        content = null,
                        error = strings.get(R.string.parse_error_no_subtitle),
                    ),
                )
            } else {
                selectedSubtitles.map { subtitle ->
                    val content = runCatching {
                        decodeSubtitleContent(extrasRepository.getSubtitleSrt(subtitle))
                    }.getOrNull()
                    if (content.isNullOrBlank()) {
                        SubtitleCopyEntry(
                            title = title,
                            subtitleName = subtitle.name,
                            content = null,
                            error = strings.get(R.string.parse_error_no_subtitle),
                        )
                    } else {
                        SubtitleCopyEntry(
                            title = title,
                            subtitleName = subtitle.name,
                            content = content,
                        )
                    }
                }
            }
        }
    }

    private suspend fun buildAiSummaryCopyEntries(
        snapshot: ParseUiState,
        info: MediaInfo,
        selectedIndices: List<Int>,
    ): List<AiSummaryCopyEntry> {
        val targets = buildDownloadTargets(snapshot, info, selectedIndices)
        return targets.map { rawItem ->
            val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                .getOrDefault(rawItem)
            val groupLabel = resolveGroupLabel(info = info, item = item)
            val title = buildSubtitleEntryTitle(groupLabel.title, groupLabel.subtitle)
            val aid = item.aid
            val cid = item.cid
            val bvid = item.bvid
            if (aid == null || cid == null || bvid.isNullOrBlank()) {
                AiSummaryCopyEntry(
                    title = title,
                    content = null,
                    error = strings.get(R.string.parse_error_no_ai),
                )
            } else {
                val summaryTitle = info.nfo.showTitle?.ifBlank { item.title } ?: item.title
                val content = runCatching {
                    extrasRepository.getAiSummaryMarkdown(summaryTitle, bvid, aid, cid)
                }.getOrNull()
                if (content.isNullOrBlank()) {
                    AiSummaryCopyEntry(
                        title = title,
                        content = null,
                        error = strings.get(R.string.parse_error_no_ai),
                    )
                } else {
                    AiSummaryCopyEntry(
                        title = title,
                        content = content,
                    )
                }
            }
        }
    }

    private suspend fun buildDownloadTargets(
        snapshot: ParseUiState,
        info: MediaInfo,
        selectedIndices: List<Int>,
    ): List<MediaItem> {
        val selectedItems = selectedIndices.mapNotNull { snapshot.items.getOrNull(it) }
        if (!snapshot.collectionMode || info.type != MediaType.Video) return selectedItems
        // 合集模式下每一集都是独立稿件，要各自展开分 P 才能拿到真正的下载单元。
        return selectedItems.flatMap { episode ->
            runCatching { mediaRepository.getVideoPages(episode) }
                .getOrDefault(listOf(episode))
                .map { page -> page.copy(title = page.title.ifBlank { episode.title }) }
        }
    }

    /** 下载列表的分组标题：给人看的，和写盘模板无关。 */
    private fun resolveGroupLabel(info: MediaInfo, item: MediaItem): DownloadGroupLabel {
        val parentTitle = info.nfo.showTitle?.trim().orEmpty()
        val itemTitle = item.title.trim()
        // 直接解析稿件时以「稿件标题（+分P号）」为准，分P标题单独拿出来信息量太低。
        if (info.type == MediaType.Video) {
            val workTitle = item.workTitle?.trim().orEmpty().ifBlank { itemTitle }
            val pageSuffix = item.page
                ?.takeIf { (item.pageCount ?: 1) > 1 }
                ?.let { " - P$it" }
                .orEmpty()
            return DownloadGroupLabel(
                title = (workTitle + pageSuffix).ifBlank { "BiliTools" },
                subtitle = null,
            )
        }
        val useParentTitle = info.type == MediaType.Bangumi || info.type == MediaType.Lesson
        val title = when {
            useParentTitle && parentTitle.isNotBlank() -> parentTitle
            itemTitle.isNotBlank() -> itemTitle
            parentTitle.isNotBlank() -> parentTitle
            else -> "BiliTools"
        }
        val subtitle = when {
            useParentTitle && parentTitle.isNotBlank() && itemTitle.isNotBlank() &&
                itemTitle != parentTitle -> itemTitle
            !useParentTitle && parentTitle.isNotBlank() && parentTitle != itemTitle -> parentTitle
            else -> null
        }
        return DownloadGroupLabel(title = title, subtitle = subtitle)
    }

    private fun createNamingSession(
        info: MediaInfo,
        items: List<MediaItem>,
    ): NamingSession {
        val session = NamingSession(
            settings = settingsRepository.currentNamingSettings(),
            downTimeEpochSeconds = System.currentTimeMillis() / 1000L,
        )
        val itemFolderCount = items
            .mapIndexed { index, item -> resolveItemFolderName(info, item, session, index + 1) }
            .filter { it.isNotBlank() }
            .distinct()
            .size
        val useTopLevelFolder = when (session.settings.topLevelFolderMode) {
            TopLevelFolderMode.Auto -> itemFolderCount > 1
            TopLevelFolderMode.Enabled -> true
            TopLevelFolderMode.Disabled -> false
        }
        if (!useTopLevelFolder) return session

        val entryShape = NamingShape.ofEntry(info.type)
        val context = NamingContextFactory.forEntry(
            info = info,
            representative = items.firstOrNull(),
            shape = entryShape,
            labels = NamingLabels(container = mapMediaTypeLabel(info.type)),
            downTimeEpochSeconds = session.downTimeEpochSeconds,
        )
        return session.copy(
            useTopLevelFolder = true,
            topLevelFolderName = renderComponent(
                shape = entryShape,
                scope = NamingTemplateScope.TopFolder,
                context = context,
                session = session,
            ),
        )
    }

    private fun buildRequestedGroupRelativePath(
        info: MediaInfo,
        item: MediaItem,
        namingSession: NamingSession,
        batchOrdinal: Int,
    ): String {
        val segments = buildList {
            add(settingsRepository.downloadRootRelativePath().replace('\\', '/').trim().trim('/'))
            if (namingSession.useTopLevelFolder) {
                namingSession.topLevelFolderName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
            add(resolveItemFolderName(info, item, namingSession, batchOrdinal))
        }.filter { it.isNotBlank() }
        return segments.joinToString("/")
    }

    private fun resolveItemFolderName(
        info: MediaInfo,
        item: MediaItem,
        namingSession: NamingSession,
        batchOrdinal: Int,
    ): String {
        return renderComponent(
            shape = NamingShape.ofItem(item.type),
            scope = NamingTemplateScope.ItemFolder,
            context = buildNamingContext(
                info = info,
                item = item,
                namingSession = namingSession,
                batchIndex = batchOrdinal,
            ),
            session = namingSession,
        )
    }

    private fun buildNamingContext(
        info: MediaInfo,
        item: MediaItem,
        namingSession: NamingSession,
        taskType: DownloadTaskType? = null,
        taskLabel: String? = null,
        mediaParams: DownloadMediaParams? = null,
        formatLabel: String? = null,
        batchIndex: Int? = null,
        imageOrdinal: String? = null,
    ): NamingContext {
        return NamingContextFactory.forItem(
            info = info,
            item = item,
            shape = NamingShape.ofItem(item.type),
            labels = NamingLabels(
                container = mapMediaTypeLabel(info.type),
                mediaType = mapMediaTypeLabel(item.type),
                taskType = taskLabel ?: taskType?.let(::mapTaskTypeLabel),
                format = formatLabel,
            ),
            downTimeEpochSeconds = namingSession.downTimeEpochSeconds,
            batchIndex = batchIndex,
            showSinglePageNumber = namingSession.settings.showSinglePageNumber,
            stream = mediaParams?.let {
                NamingStreamInfo(
                    resolution = it.resolution,
                    audioBitrate = it.audioBitrate,
                    codec = it.codec,
                )
            },
            imageOrdinal = imageOrdinal,
        )
    }

    private fun resolveTemplateFileName(
        item: MediaItem,
        namingSession: NamingSession,
        context: NamingContext,
        extension: String,
    ): String {
        return NamingRenderer.appendExtension(
            baseName = resolveTemplateBaseName(item, namingSession, context),
            extension = extension,
            cleanSeparators = namingSession.settings.cleanSeparators,
        )
    }

    private fun resolveTemplateBaseName(
        item: MediaItem,
        namingSession: NamingSession,
        context: NamingContext,
    ): String {
        return renderComponent(
            shape = NamingShape.ofItem(item.type),
            scope = NamingTemplateScope.File,
            context = context,
            session = namingSession,
        )
    }

    private fun renderComponent(
        shape: NamingShape,
        scope: NamingTemplateScope,
        context: NamingContext,
        session: NamingSession,
    ): String {
        return NamingRenderer.renderComponent(
            template = session.settings.template(shape, scope),
            context = context,
            cleanSeparators = session.settings.cleanSeparators,
        )
    }

    private fun extensionForVideoStream(stream: VideoStream): String {
        return when (stream.format) {
            StreamFormat.Dash -> "m4s"
            StreamFormat.Mp4 -> "mp4"
            StreamFormat.Flv -> "flv"
        }
    }

    private fun extensionForAudioStream(stream: AudioStream): String {
        return AudioQualities.audioFileExtension(stream.id)
    }

    private fun extensionForMergedOutput(stream: AudioStream): String {
        return AudioQualities.mergedContainerExtension(stream.id)
    }

    private fun mapMediaTypeLabel(type: MediaType): String {
        return when (type) {
            MediaType.Video -> strings.get(R.string.parse_media_type_video)
            MediaType.Bangumi -> strings.get(R.string.parse_media_type_bangumi)
            MediaType.Lesson -> strings.get(R.string.parse_media_type_lesson)
            MediaType.Music -> strings.get(R.string.parse_media_type_music)
            MediaType.MusicList -> strings.get(R.string.parse_media_type_music_list)
            MediaType.WatchLater -> strings.get(R.string.parse_media_type_watch_later)
            MediaType.Favorite -> strings.get(R.string.parse_media_type_favorite)
            MediaType.Opus -> strings.get(R.string.parse_media_type_opus)
            MediaType.OpusList -> strings.get(R.string.parse_media_type_opus_list)
            MediaType.UserVideo -> strings.get(R.string.parse_media_type_user_video)
            MediaType.UserOpus -> strings.get(R.string.parse_media_type_user_opus)
            MediaType.UserAudio -> strings.get(R.string.parse_media_type_user_audio)
        }
    }

    private fun mapTaskTypeLabel(type: DownloadTaskType): String {
        return when (type) {
            DownloadTaskType.Video -> strings.get(R.string.output_video)
            DownloadTaskType.Audio -> strings.get(R.string.output_audio)
            DownloadTaskType.AudioVideo -> strings.get(R.string.output_audio_video)
            DownloadTaskType.Subtitle -> strings.get(R.string.parse_subtitle_label)
            DownloadTaskType.AiSummary -> strings.get(R.string.parse_ai_summary_label)
            DownloadTaskType.NfoCollection -> strings.get(R.string.parse_nfo_collection)
            DownloadTaskType.NfoSingle -> strings.get(R.string.parse_nfo_single)
            DownloadTaskType.DanmakuLive -> strings.get(R.string.parse_danmaku_live)
            DownloadTaskType.DanmakuHistory -> strings.get(R.string.parse_danmaku_history)
            DownloadTaskType.Cover -> strings.get(R.string.parse_image_option_cover)
            DownloadTaskType.CollectionCover -> strings.get(R.string.parse_image_label)
            DownloadTaskType.OpusContent -> strings.get(R.string.download_task_opus_content)
            DownloadTaskType.OpusImage -> strings.get(R.string.download_task_opus_image)
        }
    }

    private fun mapStreamFormatLabel(format: StreamFormat): String {
        return when (format) {
            StreamFormat.Dash -> strings.get(R.string.format_dash)
            StreamFormat.Mp4 -> strings.get(R.string.format_mp4)
            StreamFormat.Flv -> strings.get(R.string.format_flv)
        }
    }

    private fun streamWarningFor(
        requestedFormat: StreamFormat,
        actualFormat: StreamFormat,
    ): String? {
        return when {
            requestedFormat != actualFormat -> strings.get(
                R.string.parse_warning_stream_format_fallback,
                mapStreamFormatLabel(requestedFormat),
                mapStreamFormatLabel(actualFormat),
            )
            actualFormat == StreamFormat.Dash -> strings.get(R.string.parse_warning_dash)
            else -> null
        }
    }

    private fun optimisticStreamWarningFor(format: StreamFormat): String? {
        return when (format) {
            StreamFormat.Dash -> strings.get(R.string.parse_warning_dash)
            else -> null
        }
    }

    private fun streamWarningForPendingSelectionChange(state: ParseUiState): String? {
        return state.warning ?: optimisticStreamWarningFor(state.format)
    }

    private fun mapOutputExtensionLabel(extension: String): String {
        return extension.trim().uppercase()
    }

    private fun buildSubtitleEntryTitle(title: String, subtitle: String?): String {
        return if (subtitle.isNullOrBlank()) title else "$title - $subtitle"
    }

    private fun decodeSubtitleContent(bytes: ByteArray): String {
        return bytes
            .toString(Charsets.UTF_8)
            .removePrefix("\uFEFF")
    }

    private suspend fun refreshExtras(info: MediaInfo, item: MediaItem) {
        val targetKey = item.extrasTargetKey()
        val currentTargetKey = _state.value.items
            .getOrNull(_state.value.selectedItemIndex)
            ?.extrasTargetKey()
        if (currentTargetKey != targetKey) return

        val requestGeneration = ++extrasRefreshGeneration
        if (subtitleSelectionTargetKey != targetKey) {
            subtitleSelectionTargetKey = targetKey
            _state.update {
                it.copy(
                    subtitleList = emptyList(),
                    subtitleLanguageSelection = null,
                )
            }
        }
        val capabilities = item.type.capabilities
        val isOpus = capabilities.supportsOpusExport
        val opusDocument = if (isOpus) {
            runCatching { opusRepository.getDocument(item) }
                .getOrNull()
                ?.withItemFallback(item, item.resolvedUpper(info))
        } else {
            null
        }
        val opusImagesAvailable = opusDocument?.images?.isNotEmpty()
        val resolvedItem = opusDocument?.let { document -> item.withOpusDocument(document) } ?: item
        val aid = item.aid
        val cid = item.cid
        val subtitles = if (capabilities.supportsSubtitleExport && aid != null && cid != null) {
            runCatching { extrasRepository.getSubtitles(aid, cid) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val aiAvailable = if (capabilities.supportsAiSummaryExport && aid != null && cid != null) {
            runCatching { extrasRepository.hasAiSummary(aid, cid) }.getOrDefault(false)
        } else {
            false
        }
        val collectionAvailable = capabilities.supportsNfoExport && isCollectionNfoAvailable(info)
        val thumbs = if (capabilities.supportsAuxiliaryImageExport) {
            info.nfo.thumbs.filter { it.url.isNotBlank() }
        } else {
            emptyList()
        }
        val imageOptions = thumbs
            .distinctBy { it.id }
            .map { thumb -> ImageOption(thumb.id, mapImageLabel(thumb.id)) }
        val imageOptionIds = imageOptions.map { it.id }
        val imageOptionIdSet = imageOptionIds.toSet()
        var applied = false
        _state.update { current ->
            val activeTargetKey = current.items
                .getOrNull(current.selectedItemIndex)
                ?.extrasTargetKey()
            if (requestGeneration != extrasRefreshGeneration || activeTargetKey != targetKey) {
                return@update current
            }
            applied = true
            val selectedSubtitle = pickSubtitleLanguageSelection(
                subtitles = subtitles,
                currentSelection = current.subtitleLanguageSelection,
            )
            val selectedImageIds =
                current.selectedImageIds.filter { imageOptionIdSet.contains(it) }.toSet()
            val allowMissing = current.isMultiSelect
            val selectedIndex = current.selectedItemIndex
            val resolvedItems = if (opusDocument != null && selectedIndex in current.items.indices) {
                current.items.toMutableList().also { items -> items[selectedIndex] = resolvedItem }
            } else {
                current.items
            }
            current.copy(
                items = resolvedItems,
                mediaInfo = if (resolvedItems !== current.items) {
                    current.mediaInfo?.copy(list = resolvedItems)
                } else {
                    current.mediaInfo
                },
                selectedItemStat = opusDocument?.stat ?: current.selectedItemStat,
                subtitleList = subtitles,
                subtitleLanguageSelection = selectedSubtitle,
                subtitleEnabled = current.subtitleEnabled &&
                    capabilities.supportsSubtitleExport &&
                    (allowMissing || subtitles.isNotEmpty()),
                aiSummaryAvailable = aiAvailable,
                aiSummaryEnabled = current.aiSummaryEnabled &&
                    capabilities.supportsAiSummaryExport &&
                    (allowMissing || aiAvailable),
                nfoCollectionEnabled = current.nfoCollectionEnabled &&
                    capabilities.supportsNfoExport &&
                    (allowMissing || collectionAvailable),
                nfoSingleEnabled = current.nfoSingleEnabled && capabilities.supportsNfoExport,
                danmakuLiveEnabled = current.danmakuLiveEnabled &&
                    capabilities.supportsDanmakuExport &&
                    (allowMissing || (aid != null && cid != null)),
                danmakuHistoryEnabled = current.danmakuHistoryEnabled &&
                    capabilities.supportsDanmakuExport &&
                    (allowMissing || cid != null),
                imageOptions = imageOptions,
                selectedImageIds = selectedImageIds,
                opusImagesAvailable = opusImagesAvailable,
                opusImagesEnabled = isOpus && current.opusImagesEnabled,
            ).withResolvedOpusImageSelection()
        }
        if (!applied) return
        refreshItemPresentation(info, resolvedItem, _state.value.selectedItemIndex, fromPreview = false)
    }

    private fun invalidateExtrasRefresh() {
        extrasRefreshGeneration += 1
        subtitleSelectionTargetKey = null
    }

    private fun refreshItemPresentation(
        info: MediaInfo,
        item: MediaItem,
        index: Int,
        fromPreview: Boolean,
    ) {
        val itemKey = itemCacheKey(item)
        val cachedStat = itemKey?.let { itemStatCache[it] } ?: item.stat
        val cachedDescription = itemKey?.let { itemDescriptionCache[it] }
        val itemDescription = item.description.trim()

        if (cachedStat != null || !cachedDescription.isNullOrBlank()) {
            applyItemPresentation(index, itemKey, cachedStat, cachedDescription)
        }

        if (!shouldFetchPresentationDetail(info, item, fromPreview) || itemKey == null) return
        val needStat = shouldFetchPresentationStatDetail(info, cachedStat)
        val needDescription =
            info.type == MediaType.Favorite &&
                cachedDescription.isNullOrBlank() &&
                itemDescription.isBlank()
        if (!needStat && !needDescription) return

        viewModelScope.launch(Dispatchers.IO) {
            val detail = fetchPresentationDetail(
                item = item,
                needStat = needStat,
                needDescription = needDescription,
            ) ?: return@launch
            detail.stat?.let { itemStatCache[itemKey] = it }
            detail.description?.takeIf { it.isNotBlank() }?.let { itemDescriptionCache[itemKey] = it }
            applyItemPresentation(index, itemKey, detail.stat, detail.description)
        }
    }

    private suspend fun fetchPresentationDetail(
        item: MediaItem,
        needStat: Boolean,
        needDescription: Boolean,
    ): ItemPresentationDetail? {
        val queryId = item.bvid?.takeIf { it.isNotBlank() } ?: item.aid?.toString() ?: return null
        val nfo = runCatching {
            mediaRepository.getMediaInfo(queryId, MediaType.Video).nfo
        }.getOrNull() ?: return null

        val stat = if (needStat && hasAnyStat(nfo.stat)) nfo.stat else null
        val description = if (needDescription) {
            nfo.intro?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (stat == null && description == null) return null
        return ItemPresentationDetail(stat = stat, description = description)
    }

    private fun applyItemPresentation(
        index: Int,
        itemKey: String?,
        stat: MediaStat?,
        description: String?,
    ) {
        _state.update { current ->
            // 列表可能已因翻页/切换分区被替换，仅在目标条目未变时才应用补拉结果
            val item = current.items.getOrNull(index) ?: return@update current
            if (itemKey != null && itemCacheKey(item) != itemKey) {
                return@update current
            }

            var changed = false
            var updatedItem = item

            if (!description.isNullOrBlank() && description != item.description) {
                updatedItem = updatedItem.copy(description = description)
                changed = true
            }

            val resolvedStat = stat ?: updatedItem.stat
            if (resolvedStat != null && resolvedStat != updatedItem.stat) {
                updatedItem = updatedItem.copy(stat = resolvedStat)
                changed = true
            }

            val updatedItems = if (changed) {
                current.items.toMutableList().also { list ->
                    list[index] = updatedItem
                }
            } else {
                current.items
            }

            // 仅当补拉的是当前项时才同步 selectedItemStat，预览其他条目不影响当前项
            val nextSelectedItemStat = if (index == current.selectedItemIndex) {
                resolvedStat ?: current.selectedItemStat
            } else {
                current.selectedItemStat
            }
            if (!changed && nextSelectedItemStat == current.selectedItemStat) {
                return@update current
            }

            current.copy(
                items = updatedItems,
                selectedItemStat = nextSelectedItemStat,
            )
        }
    }

    private fun shouldFetchPresentationDetail(
        info: MediaInfo,
        item: MediaItem,
        fromPreview: Boolean,
    ): Boolean {
        return when (info.type) {
            // 合集列表接口不返回选集的 stat，仅在用户点击预览时补拉该视频详情，
            // 避免解析合集时为默认选集额外多发一次请求
            MediaType.Video -> fromPreview && info.collection && item.bvid?.isNotBlank() == true
            MediaType.Favorite -> item.type == MediaType.Video && item.bvid?.isNotBlank() == true
            MediaType.WatchLater -> item.type == MediaType.Video &&
                (item.bvid?.isNotBlank() == true || item.aid != null)
            else -> false
        }
    }

    private fun shouldFetchPresentationStatDetail(info: MediaInfo, stat: MediaStat?): Boolean {
        if (stat == null) return true
        return when (info.type) {
            // 合集选集与收藏夹的列表接口都不含评论/点赞/投币/分享，缺失时补拉完整详情
            MediaType.Video,
            MediaType.Favorite,
            -> stat.reply == null ||
                stat.like == null ||
                stat.coin == null ||
                stat.share == null
            MediaType.WatchLater -> !hasAnyStat(stat)
            else -> !hasAnyStat(stat)
        }
    }

    private fun itemCacheKey(item: MediaItem): String? {
        val bvid = item.bvid?.trim().orEmpty()
        if (bvid.isNotBlank()) {
            return "bvid:$bvid"
        }
        return item.aid?.let { aid -> "aid:$aid" }
    }

    private fun hasAnyStat(stat: MediaStat): Boolean {
        return stat.play != null ||
            stat.danmaku != null ||
            stat.reply != null ||
            stat.like != null ||
            stat.coin != null ||
            stat.favorite != null ||
            stat.share != null
    }

    private fun applyDefaultDownloadQuality(state: ParseUiState): ParseUiState {
        val quality = settingsRepository.currentDefaultDownloadQuality()
        return state.copy(
            resolutionMode = quality.resolutionMode.toQualityMode(),
            selectedResolutionId = quality.fixedResolutionId.takeIf {
                quality.resolutionMode == DownloadQualityMode.Fixed
            },
            selectedCodec = quality.codec.toVideoCodec(),
            audioBitrateMode = quality.audioBitrateMode.toQualityMode(),
            selectedAudioId = quality.fixedAudioBitrateId.takeIf {
                quality.audioBitrateMode == DownloadQualityMode.Fixed
            },
        )
    }

    private fun DownloadQualityMode.toQualityMode(): QualityMode {
        return when (this) {
            DownloadQualityMode.Highest -> QualityMode.Highest
            DownloadQualityMode.Lowest -> QualityMode.Lowest
            DownloadQualityMode.Fixed -> QualityMode.Fixed
        }
    }

    private fun DefaultDownloadVideoCodec.toVideoCodec(): VideoCodec {
        return when (this) {
            DefaultDownloadVideoCodec.Avc -> VideoCodec.Avc
            DefaultDownloadVideoCodec.Hevc -> VideoCodec.Hevc
            DefaultDownloadVideoCodec.Av1 -> VideoCodec.Av1
        }
    }

    private fun normalizeQualityModes(state: ParseUiState): ParseUiState {
        val selectedCount = state.selectedItemIndices.size
        val nextResolutionMode = state.resolutionMode
        val nextAudioMode = state.audioBitrateMode
        val nextResolutions = resolveResolutionOptions(state.videoStreams, nextResolutionMode, selectedCount)
        val nextAudioOptions = resolveAudioOptions(state.audioStreams, nextAudioMode, selectedCount)
        val nextResolutionId = pickResolutionId(state.selectedResolutionId, nextResolutions, nextResolutionMode)
        val nextCodecs = buildCodecOptionsForSelection(
            state.videoStreams,
            nextResolutionId,
            nextResolutionMode,
        )
        val nextAudioId = pickAudioId(state.selectedAudioId, nextAudioOptions, nextAudioMode)
        return state.copy(
            resolutionMode = nextResolutionMode,
            audioBitrateMode = nextAudioMode,
            resolutions = nextResolutions,
            codecs = nextCodecs,
            audioBitrates = nextAudioOptions,
            selectedResolutionId = nextResolutionId,
            selectedCodec = pickCodec(state.selectedCodec, nextCodecs),
            selectedAudioId = nextAudioId,
        )
    }

    private fun currentItem(state: ParseUiState = _state.value): MediaItem? {
        return state.items.getOrNull(state.selectedItemIndex)
    }

    private fun ParseUiState.autoStreamRequestKeyOrNull(): StreamRequestKey? {
        if (!canAutoLoadStream()) return null
        return streamRequestKeyOrNull()
    }

    private fun ParseUiState.streamRequestKeyOrNull(): StreamRequestKey? {
        val info = mediaInfo ?: return null
        if (selectedItemIndex !in items.indices) return null
        return StreamRequestKey(
            mediaId = info.id,
            mediaType = info.type,
            selectedSectionId = selectedSectionId,
            pageIndex = pageIndex,
            collectionMode = collectionMode,
            selectedItemIndex = selectedItemIndex,
            format = format,
        )
    }

    private fun resetStreamLoadTracking() {
        streamLoadGeneration += 1
        loadedStreamKey = null
        loadingStreamKey = null
        failedStreamKey = null
    }

    private fun invalidateActiveStreamLoad() {
        streamLoadGeneration += 1
        loadingStreamKey = null
    }

    private fun selectVideoStream(state: ParseUiState): VideoStream? {
        return selectVideoStream(
            state.videoStreams,
            state.selectedResolutionId,
            state.selectedCodec,
            state.resolutionMode,
        )
    }

    private fun selectVideoStream(
        streams: List<VideoStream>,
        resolutionId: Int?,
        codec: VideoCodec?,
        mode: QualityMode,
    ): VideoStream? {
        if (streams.isEmpty()) return null
        val targetId = resolveResolutionId(streams, resolutionId, mode)
        val resolutionCandidates = targetId?.let { id ->
            streams.filter { it.id == id }
        } ?: streams
        val codecCandidates = codec?.let { codecValue ->
            val filtered = resolutionCandidates.filter { stream ->
                val actual = stream.codec ?: VideoCodec.Avc
                actual == codecValue
            }
            if (filtered.isNotEmpty()) filtered else resolutionCandidates
        } ?: resolutionCandidates
        return codecCandidates.maxByOrNull { it.bandwidth ?: 0 }
            ?: resolutionCandidates.maxByOrNull { it.bandwidth ?: 0 }
            ?: streams.first()
    }

    private fun selectVideoStreamForMerge(
        state: ParseUiState,
        current: VideoStream?,
    ): VideoStream? {
        return selectVideoStreamForMerge(state.videoStreams, current)
    }

    private fun selectVideoStreamForMerge(
        streams: List<VideoStream>,
        current: VideoStream?,
    ): VideoStream? {
        if (current == null) return null
        if (current.codec != VideoCodec.Av1) return current
        val sameResolution = streams.filter { it.id == current.id }
        return sameResolution.firstOrNull { it.codec == VideoCodec.Avc }
            ?: sameResolution.firstOrNull { it.codec == VideoCodec.Hevc }
            ?: current
    }

    private fun selectAudioStream(state: ParseUiState): AudioStream? {
        return selectAudioStream(state.audioStreams, state.selectedAudioId, state.audioBitrateMode)
    }

    private fun selectAudioStream(
        streams: List<AudioStream>,
        selectedId: Int?,
        mode: QualityMode,
    ): AudioStream? {
        if (streams.isEmpty()) return null
        val targetId = resolveAudioId(streams, selectedId, mode)
        val candidates = targetId?.let { id -> streams.filter { it.id == id } } ?: streams
        return candidates.maxByOrNull { it.bandwidth ?: 0 }
            ?: streams.maxByOrNull { it.bandwidth ?: 0 }
    }

    private fun resolveResolutionId(
        streams: List<VideoStream>,
        resolutionId: Int?,
        mode: QualityMode,
    ): Int? {
        if (streams.isEmpty()) return null
        val ids = streams.map { it.id }.distinct()
        return when (mode) {
            QualityMode.Highest -> ids.maxOrNull()
            QualityMode.Lowest -> ids.minOrNull()
            QualityMode.Fixed -> resolutionId?.takeIf { ids.contains(it) } ?: ids.maxOrNull()
        }
    }

    private fun resolveAudioId(
        streams: List<AudioStream>,
        selectedId: Int?,
        mode: QualityMode,
    ): Int? {
        if (streams.isEmpty()) return null
        val ids = streams.map { it.id }.distinct()
        return when (mode) {
            QualityMode.Highest -> AudioQualities.highest(ids)
            QualityMode.Lowest -> AudioQualities.lowest(ids)
            QualityMode.Fixed -> selectedId?.takeIf { ids.contains(it) } ?: AudioQualities.highest(ids)
        }
    }

    private fun resolveResolutionOptions(
        streams: List<VideoStream>,
        mode: QualityMode,
        selectedCount: Int,
    ): List<QualityOption> {
        return if (selectedCount > 1 && mode == QualityMode.Fixed) {
            buildResolutionOptionsAll(streams)
        } else {
            buildResolutionOptions(streams)
        }
    }

    private fun resolveAudioOptions(
        streams: List<AudioStream>,
        mode: QualityMode,
        selectedCount: Int,
    ): List<AudioOption> {
        return if (selectedCount > 1 && mode == QualityMode.Fixed) {
            buildAudioOptionsAll(streams)
        } else {
            buildAudioOptions(streams)
        }
    }

    private fun pickResolutionId(
        currentId: Int?,
        options: List<QualityOption>,
        mode: QualityMode,
    ): Int? {
        if (options.isEmpty()) return currentId
        return when (mode) {
            QualityMode.Highest -> options.maxByOrNull { it.id }?.id
            QualityMode.Lowest -> options.minByOrNull { it.id }?.id
            QualityMode.Fixed -> currentId?.takeIf { id -> options.any { it.id == id } } ?: options.first().id
        }
    }

    private fun pickAudioId(
        currentId: Int?,
        options: List<AudioOption>,
        mode: QualityMode,
    ): Int? {
        if (options.isEmpty()) return currentId
        return when (mode) {
            QualityMode.Highest -> AudioQualities.highest(options.map { it.id })
            QualityMode.Lowest -> AudioQualities.lowest(options.map { it.id })
            QualityMode.Fixed -> currentId?.takeIf { id -> options.any { it.id == id } } ?: options.first().id
        }
    }

    private fun buildResolutionOptions(streams: List<VideoStream>): List<QualityOption> {
        return streams.map { stream ->
            QualityOption(stream.id, mapResolutionLabel(stream))
        }.distinctBy { it.id }.sortedByDescending { it.id }
    }

    private fun buildResolutionOptionsAll(streams: List<VideoStream>): List<QualityOption> {
        val known = fullResolutionIds.map { id ->
            QualityOption(id, mapResolutionLabel(id, null))
        }
        return (known + buildResolutionOptions(streams))
            .distinctBy { it.id }
            .sortedByDescending { it.id }
    }

    private fun buildCodecOptions(streams: List<VideoStream>): List<CodecOption> {
        val codecs = streams.map { it.codec ?: VideoCodec.Avc }.distinct()
        val ordered = codecs.sortedBy { codec ->
            when (codec) {
                VideoCodec.Avc -> 0
                VideoCodec.Hevc -> 1
                VideoCodec.Av1 -> 2
            }
        }
        return ordered.map { codec ->
            CodecOption(codec, codecLabel(codec))
        }
    }

    private fun buildCodecOptionsForSelection(
        streams: List<VideoStream>,
        resolutionId: Int?,
        mode: QualityMode,
    ): List<CodecOption> {
        if (streams.isEmpty()) return emptyList()
        val targetId = resolveResolutionId(streams, resolutionId, mode)
        val candidates = targetId?.let { id ->
            streams.filter { it.id == id }
        }.orEmpty()
        return buildCodecOptions(candidates.ifEmpty { streams })
    }

    private fun pickCodec(
        currentCodec: VideoCodec?,
        options: List<CodecOption>,
    ): VideoCodec? {
        return currentCodec
            ?.takeIf { codec -> options.any { it.codec == codec } }
            ?: pickDefaultCodec(options)
    }

    private fun buildAudioOptions(streams: List<AudioStream>): List<AudioOption> {
        val labelsById = streams.associate { it.id to mapAudioLabel(it.id) }
        return AudioQualities.sortDescending(labelsById.keys).map { id ->
            AudioOption(id, labelsById[id] ?: mapAudioLabel(id))
        }
    }

    private fun buildAudioOptionsAll(streams: List<AudioStream>): List<AudioOption> {
        val known = fullAudioIds.map { id ->
            AudioOption(id, mapAudioLabel(id))
        }
        return (known + buildAudioOptions(streams))
            .distinctBy { it.id }
            .sortedByDescending { AudioQualities.allIds.indexOf(it.id) }
    }

    private fun mapResolutionLabel(stream: VideoStream): String {
        return mapResolutionLabel(stream.id, stream.height)
    }

    private fun mapResolutionLabel(id: Int, height: Int?): String {
        return when (id) {
            127 -> strings.get(R.string.parse_resolution_8k)
            126 -> strings.get(R.string.parse_resolution_dolby)
            125 -> strings.get(R.string.parse_resolution_hdr)
            120 -> strings.get(R.string.parse_resolution_4k)
            116 -> strings.get(R.string.parse_resolution_1080_60)
            112 -> strings.get(R.string.parse_resolution_1080_high)
            80 -> strings.get(R.string.parse_resolution_1080)
            64 -> strings.get(R.string.parse_resolution_720)
            32 -> strings.get(R.string.parse_resolution_480)
            16 -> strings.get(R.string.parse_resolution_360)
            6 -> strings.get(R.string.parse_resolution_240)
            else -> {
                val resolvedHeight = height ?: 0
                when {
                    resolvedHeight >= 4320 -> strings.get(R.string.parse_resolution_8k)
                    resolvedHeight >= 2160 -> strings.get(R.string.parse_resolution_4k)
                    resolvedHeight >= 1080 -> strings.get(R.string.parse_resolution_1080)
                    resolvedHeight >= 720 -> strings.get(R.string.parse_resolution_720)
                    resolvedHeight >= 480 -> strings.get(R.string.parse_resolution_480)
                    resolvedHeight >= 360 -> strings.get(R.string.parse_resolution_360)
                    else -> strings.get(R.string.parse_resolution_other)
                }
            }
        }
    }

    private fun codecLabel(codec: VideoCodec): String {
        return when (codec) {
            VideoCodec.Avc -> strings.get(R.string.parse_codec_avc)
            VideoCodec.Hevc -> strings.get(R.string.parse_codec_hevc)
            VideoCodec.Av1 -> strings.get(R.string.parse_codec_av1)
        }
    }

    private fun mapAudioLabel(id: Int): String {
        return strings.get(AudioQualities.labelRes(id))
    }

    private fun imageDownloadTaskType(imageId: String): DownloadTaskType {
        return when (imageId.substringBefore('-')) {
            "cover", "pic" -> DownloadTaskType.Cover
            else -> DownloadTaskType.CollectionCover
        }
    }

    private fun mapImageLabel(id: String): String {
        val base = id.substringBefore('-')
        val suffix = id.substringAfter('-', "")
        return when (base) {
            "pic", "cover" -> strings.get(R.string.parse_image_option_cover)
            "square_cover" -> strings.get(R.string.parse_image_option_square_cover)
            "first_frame" -> strings.get(R.string.parse_image_option_first_frame)
            "ugc", "ugc_season_cover" -> strings.get(R.string.parse_image_option_ugc_cover)
            "season_cover" -> strings.get(R.string.parse_image_option_season_cover)
            "season_horizontal_cover_1610" ->
                strings.get(R.string.parse_image_option_season_horizontal_cover_1610)
            "season_horizontal_cover_169" ->
                strings.get(R.string.parse_image_option_season_horizontal_cover_169)
            "brief" -> {
                val index = suffix.toIntOrNull()
                if (index != null) {
                    strings.get(R.string.parse_image_option_brief, index)
                } else {
                    id
                }
            }
            else -> id
        }
    }

    private fun buildMediaParams(
        video: VideoStream?,
        codec: VideoCodec?,
        audio: AudioStream?,
    ): DownloadMediaParams? {
        val resolution = video?.let { mapResolutionLabel(it) }
        val codecLabel = codec?.let { codecLabel(it) }
        val audioLabel = audio?.let { mapAudioLabel(it.id) }
        if (resolution.isNullOrBlank() && codecLabel.isNullOrBlank() && audioLabel.isNullOrBlank()) {
            return null
        }
        return DownloadMediaParams(
            resolution = resolution,
            codec = codecLabel,
            audioBitrate = audioLabel,
        )
    }

    private fun pickDefaultCodec(options: List<CodecOption>): VideoCodec? {
        val preferredCodec = settingsRepository.currentDefaultDownloadQuality().codec.toVideoCodec()
        return options.firstOrNull { it.codec == preferredCodec }?.codec
            ?: options.firstOrNull { it.codec == VideoCodec.Avc }?.codec
            ?: options.firstOrNull { it.codec == VideoCodec.Hevc }?.codec
            ?: options.firstOrNull()?.codec
    }

    private fun buildVideoFileName(
        base: String,
        stream: VideoStream,
        codec: VideoCodec?,
    ): String {
        val ext = when (stream.format) {
            StreamFormat.Dash -> "m4s"
            StreamFormat.Mp4 -> "mp4"
            StreamFormat.Flv -> "flv"
        }
        val resolution = mapResolutionLabel(stream)
        val codecLabel = codec?.let { codecLabel(it) }
        val suffix = listOfNotNull(resolution, codecLabel).joinToString("-")
        return sanitizeFileName("$base-$suffix.$ext")
    }

    private fun buildMergedFileName(
        base: String,
        stream: VideoStream,
        codec: VideoCodec?,
    ): String {
        val resolution = mapResolutionLabel(stream)
        val codecLabel = codec?.let { codecLabel(it) }
        val suffix = listOfNotNull(resolution, codecLabel).joinToString("-")
        return sanitizeFileName("$base-$suffix.mp4")
    }

    private fun buildAudioFileName(base: String, stream: AudioStream): String {
        val bitrate = mapAudioLabel(stream.id)
        return sanitizeFileName("$base-$bitrate.m4a")
    }

    private fun buildEmbeddedMetadata(
        info: MediaInfo,
        item: MediaItem,
        fallbackAlbum: String,
        trackTotal: Int?,
    ): DownloadEmbeddedMetadata {
        val album = info.nfo.showTitle
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackAlbum.trim().takeIf { it.isNotBlank() }

        val title = item.title.trim().takeIf { it.isNotBlank() } ?: album
        val artist = item.resolvedUpper(info)?.name?.trim()?.takeIf { it.isNotBlank() }
        val comment = item.description.trim().takeIf { it.isNotBlank() }
            ?: info.nfo.intro?.trim()?.takeIf { it.isNotBlank() }

        val ts = item.pubTime.takeIf { it > 0 } ?: info.nfo.premiered
        val date = ts?.let { formatShanghaiDate(it) }
        val year = date?.take(4)?.toIntOrNull()

        val tags = info.nfo.tags.map { it.trim() }.filter { it.isNotBlank() }

        val normalizedTotal = trackTotal?.takeIf { it > 0 }
        val trackNumber = normalizedTotal
            ?.takeIf { it > 1 }
            ?.let { item.index + 1 }
            ?.takeIf { it > 0 }

        val originalUrl = item.url.trim().takeIf { it.isNotBlank() }
            ?: info.nfo.url?.trim()?.takeIf { it.isNotBlank() }
        val coverUrl = item.coverUrl.trim().takeIf { it.isNotBlank() }
            ?: info.nfo.thumbs.firstOrNull()?.url?.trim()?.takeIf { it.isNotBlank() }

        return DownloadEmbeddedMetadata(
            title = title,
            album = album,
            artist = artist,
            albumArtist = artist,
            comment = comment,
            date = date,
            year = year,
            tags = tags,
            trackNumber = trackNumber,
            trackTotal = normalizedTotal,
            originalUrl = originalUrl,
            coverUrl = coverUrl,
        )
    }

    private fun formatShanghaiDate(epochSeconds: Long): String {
        val offset = java.time.ZoneOffset.ofHours(8)
        return java.time.Instant
            .ofEpochSecond(epochSeconds)
            .atOffset(offset)
            .toLocalDate()
            .toString()
    }

    private fun addUnavailableExtraTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        reason: String,
        fileName: String = "",
    ) {
        downloadRepository.addUnavailableTask(
            groupId = groupId,
            type = type,
            taskTitle = taskTitle,
            reason = reason,
            fileName = fileName,
        )
    }

    private fun isCollectionNfoAvailable(info: MediaInfo): Boolean {
        if (info.nfo.showTitle.isNullOrBlank()) return false
        return info.collection ||
            info.type == MediaType.Bangumi ||
            info.type == MediaType.Lesson
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun extensionFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#")
        val ext = clean.substringAfterLast('.', "")
        return if (ext.isBlank() || ext.length > 4) "jpg" else ext
    }

    private fun isValidDate(value: String): Boolean {
        return runCatching { LocalDate.parse(value) }.isSuccess
    }

    private fun parseHour(value: String): Int? {
        if (value.isBlank()) return null
        val parsed = value.toIntOrNull() ?: return null
        return parsed.takeIf { it in 0..23 }
    }

    private fun mapError(err: Throwable): String {
        return when (err) {
            is InvalidMediaInputException -> strings.get(R.string.parse_error_invalid_input)
            is OpusException -> when (err.failure) {
                OpusFailure.InvalidReference -> strings.get(R.string.parse_error_invalid_input)
                OpusFailure.RiskControl -> strings.get(R.string.parse_error_opus_risk_control)
                OpusFailure.LoginRequired -> strings.get(R.string.parse_error_opus_login_required)
                OpusFailure.PermissionDenied -> strings.get(R.string.parse_error_opus_permission_denied)
                OpusFailure.NotFound -> strings.get(R.string.parse_error_opus_not_found)
                OpusFailure.InvalidResponse -> strings.get(R.string.parse_error_opus_invalid_response)
                OpusFailure.ApiError -> formatBiliApiErrorMessage(
                    message = err.message?.takeUnless { it == err.failure.name },
                    code = err.apiCode ?: 0,
                    fallback = strings.get(R.string.parse_error_failed),
                )
            }
            is BiliHttpException -> formatBiliApiErrorMessage(
                message = err.message,
                code = err.code,
                fallback = strings.get(R.string.parse_error_failed),
            )
            else -> err.message?.takeIf { it.isNotBlank() } ?: strings.get(R.string.common_error_unknown)
        }
    }

    companion object {
        private const val OPUS_DETAIL_DOWNLOAD_PARALLELISM = 4
    }

    private fun setLoadingError(err: Throwable) {
        _state.update {
            it.copy(
                loading = false,
                collectionModeLoading = false,
                error = mapError(err),
                isLoggedIn = authRepository.isLoggedIn(),
            )
        }
    }

    private fun setStreamLoadingError(err: Throwable) {
        _state.update {
            it.copy(
                streamLoading = false,
                error = mapError(err),
                isLoggedIn = authRepository.isLoggedIn(),
            )
        }
    }
}









