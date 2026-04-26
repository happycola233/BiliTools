package com.happycola233.bilitools.ui.parse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLog
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.DownloadNaming
import com.happycola233.bilitools.core.NamingRenderContext
import com.happycola233.bilitools.core.NfoGenerator
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.DefaultDownloadVideoCodec
import com.happycola233.bilitools.data.DanmakuLiveProgress
import com.happycola233.bilitools.data.DanmakuLiveProgressPhase
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.data.ExportRepository
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.MediaRepository
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.TopLevelFolderMode
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaSections
import com.happycola233.bilitools.data.model.MediaStat
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.PlayUrlInfo
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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

private data class DownloadTargets(
    val isCollectionMode: Boolean,
    val items: List<MediaItem>,
    val pageCountByBvid: Map<String, Int>,
    val videoTitleByBvid: Map<String, String>,
)

private data class ExtraTaskProgress(
    val progress: Int,
    val statusDetail: String? = null,
    val progressIndeterminate: Boolean = false,
)

private data class GroupNaming(
    val groupTitle: String,
    val groupSubtitle: String?,
    val baseName: String,
    val useVideoNaming: Boolean,
    val pageCount: Int,
    val videoTitle: String,
)

private data class NamingSession(
    val useTopLevelFolder: Boolean,
    val topLevelFolderTemplate: String,
    val itemFolderTemplate: String,
    val fileTemplate: String,
    val cleanSeparators: Boolean,
    val downTimeEpochSeconds: Long,
    val topLevelFolderName: String?,
)

enum class QualityMode {
    Highest,
    Lowest,
    Fixed,
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
}

data class ParseUiState(
    val loading: Boolean = false,
    val streamLoading: Boolean = false,
    val collectionModeLoading: Boolean = false,
    val downloadStarting: Boolean = false,
    val subtitleCopying: Boolean = false,
    val aiSummaryCopying: Boolean = false,
    val error: String? = null,
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
    val subtitleList: List<SubtitleInfo> = emptyList(),
    val selectedSubtitleLan: String? = null,
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
    val warning: String? = null,
    val collectionPreviewIndex: Int? = null,
    val collectionPreviewStat: MediaStat? = null,
    val selectedItemStat: MediaStat? = null,
    val lastDownload: DownloadItem? = null,
    val isLoggedIn: Boolean = false,
)

private fun defaultDate(): String {
    return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
}

class ParseViewModel(
    private val mediaRepository: MediaRepository,
    private val extrasRepository: ExtrasRepository,
    private val downloadRepository: DownloadRepository,
    private val exportRepository: ExportRepository,
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(applyDefaultDownloadQuality(ParseUiState()))
    val state: StateFlow<ParseUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ParseEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ParseEvent> = _events.asSharedFlow()
    private val fullResolutionIds = listOf(127, 126, 125, 120, 116, 112, 80, 64, 32, 16, 6)
    private val fullAudioIds = AudioQualities.allIds
    private val offsetMap = mutableMapOf<Int, String>()
    private val collectionStatCache = mutableMapOf<String, MediaStat>()
    private val itemStatCache = mutableMapOf<String, MediaStat>()
    private val itemDescriptionCache = mutableMapOf<String, String>()
    private var streamLoadGeneration = 0L
    private var loadedStreamKey: StreamRequestKey? = null
    private var loadingStreamKey: StreamRequestKey? = null
    private var failedStreamKey: StreamRequestKey? = null

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
            offsetMap.clear()
            collectionStatCache.clear()
            itemStatCache.clear()
            itemDescriptionCache.clear()
            runCatching {
                val allowRaw = _state.value.selectedMediaType != null
                val parsed = mediaRepository.parseInput(input, allowRaw)
                val resolvedType =
                    _state.value.selectedMediaType ?: parsed.type ?: throw IllegalArgumentException("Invalid input")
                val info = mediaRepository.getMediaInfo(
                    parsed.id,
                    resolvedType,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(target = parsed.target),
                )
                val defaultIndex =
                    info.list.indexOfFirst { it.isTarget }.takeIf { it >= 0 } ?: 0
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
                                outputType = OutputType.AudioVideo,
                                warning = null,
                                collectionPreviewIndex = null,
                                collectionPreviewStat = null,
                                selectedItemStat = info.list.getOrNull(defaultIndex)?.stat,
                                streamLoading = info.list.isNotEmpty(),
                                isLoggedIn = authRepository.isLoggedIn(),
                            ),
                        ),
                    )
                }
                info.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(info, item)
                }
                if (info.type == MediaType.UserOpus && info.offset != null) {
                    offsetMap[1] = ""
                }
            }.onFailure { err ->
                setLoadingError(err)
            }
        }
    }

    fun clear() {
        val selectedType = _state.value.selectedMediaType
        resetStreamLoadTracking()
        offsetMap.clear()
        collectionStatCache.clear()
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

    fun selectItem(index: Int, ensureSelected: Boolean = true) {
        val info = _state.value.mediaInfo ?: return
        val item = _state.value.items.getOrNull(index) ?: return
        val selectedItems = _state.value.selectedItemIndices.toMutableList()
        if (ensureSelected && !selectedItems.contains(index)) {
            selectedItems.add(index)
            selectedItems.sort()
        }
        _state.update {
            normalizeQualityModes(
                it.copy(
                    selectedItemIndex = index,
                    selectedItemIndices = selectedItems,
                    selectedItemStat = item.stat,
                    warning = streamWarningForPendingSelectionChange(it),
                ),
            )
        }
        viewModelScope.launch {
            refreshExtras(info, item)
        }
    }

    fun toggleItemSelection(index: Int) {
        val items = _state.value.items
        if (index !in items.indices) return
        val selected = _state.value.selectedItemIndices.toMutableList()
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
        if (currentChanged && info != null && nextItem != null) {
            viewModelScope.launch {
                refreshExtras(info, nextItem)
            }
        }
    }

    fun previewCollectionItem(index: Int) {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (!snapshot.collectionMode || info.type != MediaType.Video || !info.collection) return
        val item = snapshot.items.getOrNull(index) ?: return
        val bvid = item.bvid?.trim().orEmpty()
        val cached = if (bvid.isNotBlank()) collectionStatCache[bvid] else null
        _state.update {
            it.copy(
                collectionPreviewIndex = index,
                collectionPreviewStat = cached,
            )
        }
        if (cached != null || bvid.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val stat = runCatching {
                mediaRepository.getMediaInfo(bvid, MediaType.Video).nfo.stat
            }.getOrNull() ?: return@launch

            collectionStatCache[bvid] = stat
            _state.update { current ->
                val previewIndex = current.collectionPreviewIndex ?: return@update current
                val previewBvid = current.items.getOrNull(previewIndex)?.bvid?.trim().orEmpty()
                if (previewBvid != bvid) return@update current
                current.copy(collectionPreviewStat = stat)
            }
        }
    }

    fun onItemRowClick(index: Int) {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (info.type == MediaType.Video) {
            if (snapshot.collectionMode && info.collection) {
                previewCollectionItem(index)
            }
            return
        }
        selectItem(index, ensureSelected = false)
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
            offsetMap[targetPage] ?: info.offset
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
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                            streamLoading = updated.list.isNotEmpty() && it.outputType != null,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
                if (updated.type == MediaType.UserOpus && updated.offset != null) {
                    if (targetPage == 1) {
                        offsetMap[1] = ""
                    }
                    offsetMap[targetPage + 1] = updated.offset
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
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
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
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
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
        _state.update { it.copy(subtitleEnabled = enabled) }
    }

    fun setSubtitleLanguage(lan: String) {
        _state.update { it.copy(selectedSubtitleLan = lan) }
    }

    fun setAiSummaryEnabled(enabled: Boolean) {
        _state.update { it.copy(aiSummaryEnabled = enabled) }
    }

    fun setNfoCollectionEnabled(enabled: Boolean) {
        _state.update { it.copy(nfoCollectionEnabled = enabled) }
    }

    fun setNfoSingleEnabled(enabled: Boolean) {
        _state.update { it.copy(nfoSingleEnabled = enabled) }
    }

    fun setDanmakuLiveEnabled(enabled: Boolean) {
        _state.update { it.copy(danmakuLiveEnabled = enabled) }
    }

    fun setDanmakuHistoryEnabled(enabled: Boolean) {
        _state.update { it.copy(danmakuHistoryEnabled = enabled) }
    }

    fun setDanmakuDate(value: String) {
        _state.update { it.copy(danmakuDate = value) }
    }

    fun setDanmakuHour(value: String) {
        _state.update { it.copy(danmakuHour = value) }
    }

    fun setImageSelection(id: String, selected: Boolean) {
        _state.update {
            val updated = it.selectedImageIds.toMutableSet()
            if (selected) {
                updated.add(id)
            } else {
                updated.remove(id)
            }
            it.copy(selectedImageIds = updated)
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
        val state = _state.value
        val info = state.mediaInfo ?: return
        val selectedIndices = state.selectedItemIndices.filter { it in state.items.indices }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
            return
        }
        if (state.outputType != null && state.playUrlInfo == null) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_stream)) }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    downloadStarting = true,
                    error = null,
                    notice = null,
                    // External dialog watches lastDownload to decide when it can close.
                    // Reset before a new attempt to avoid stale success signal.
                    lastDownload = null,
                )
            }
            withContext(Dispatchers.IO) {
                downloadRepository.ensureLoaded()
            }
            _state.update {
                it.copy(
                    downloadStarting = false,
                    notice = strings.get(R.string.parse_notice_download_started),
                )
            }
            val snapshot = state
            viewModelScope.launch(Dispatchers.IO) {
                val targets = buildDownloadTargets(snapshot, info, selectedIndices)
                val namingSession = createNamingSession(
                    info = info,
                    targets = targets,
                )
                var lastDownload: DownloadItem? = null
                targets.items.forEach { rawItem ->
                    val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                        .getOrDefault(rawItem)
                    val playUrlInfo = if (snapshot.outputType != null) {
                        runCatching {
                            mediaRepository.getPlayUrlInfo(item, item.type, snapshot.format)
                        }.getOrNull()
                    } else {
                        null
                    }
                    val naming = resolveGroupNaming(
                        info = info,
                        item = item,
                        isCollectionMode = targets.isCollectionMode,
                        pageCountByBvid = targets.pageCountByBvid,
                        videoTitleByBvid = targets.videoTitleByBvid,
                    )

                    val requestedGroupRelativePath = buildRequestedGroupRelativePath(
                        info = info,
                        item = item,
                        naming = naming,
                        namingSession = namingSession,
                    )

                    val groupId = downloadRepository.createGroup(
                        naming.groupTitle,
                        naming.groupSubtitle,
                        item.bvid,
                        item.coverUrl,
                        relativePath = requestedGroupRelativePath,
                    )
                    val groupRelativePath = downloadRepository.groupRelativePath(groupId)

                    val trackTotal = when {
                        naming.useVideoNaming && naming.pageCount > 0 -> naming.pageCount
                        !info.paged && info.list.size > 1 -> info.list.size
                        else -> null
                    }
                    val embeddedMetadata = buildEmbeddedMetadata(
                        info = info,
                        item = item,
                        fallbackAlbum = if (naming.useVideoNaming && naming.videoTitle.isNotBlank()) {
                            naming.videoTitle
                        } else {
                            naming.groupTitle
                        },
                        trackTotal = trackTotal,
                    )

                    val outputType = snapshot.outputType
                    if (outputType != null && playUrlInfo == null) {
                        _state.update { it.copy(error = strings.get(R.string.parse_error_no_stream)) }
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
                        var canDownloadMedia = true
                        when (outputType) {
                            OutputType.AudioOnly -> {
                                if (selectedAudio == null) {
                                    _state.update { it.copy(error = strings.get(R.string.parse_error_no_audio)) }
                                    canDownloadMedia = false
                                }
                            }
                            OutputType.VideoOnly -> {
                                if (selectedVideo == null) {
                                    _state.update { it.copy(error = strings.get(R.string.parse_error_no_url)) }
                                    canDownloadMedia = false
                                }
                            }
                            OutputType.AudioVideo -> {
                                if (mergeVideo == null) {
                                    _state.update { it.copy(error = strings.get(R.string.parse_error_no_url)) }
                                    canDownloadMedia = false
                                }
                            }
                        }
                        if (canDownloadMedia) {
                            val downloadTitle = when (outputType) {
                                OutputType.AudioOnly -> strings.get(R.string.output_audio)
                                OutputType.VideoOnly -> strings.get(R.string.output_video)
                                OutputType.AudioVideo -> strings.get(R.string.output_audio_video)
                            }
                            val outputVideoCodec = when (outputType) {
                                OutputType.AudioOnly -> null
                                OutputType.VideoOnly -> selectedVideo?.codec ?: snapshot.selectedCodec
                                OutputType.AudioVideo -> mergeVideo?.codec ?: selectedVideo?.codec ?: snapshot.selectedCodec
                            }
                            when (outputType) {
                                OutputType.AudioOnly -> {
                                    val mediaParams = buildMediaParams(null, null, selectedAudio)
                                    val audioExtension = extensionForAudioStream(selectedAudio!!)
                                    val audioNamingContext = buildNamingRenderContext(
                                        info = info,
                                        item = item,
                                        naming = naming,
                                        namingSession = namingSession,
                                        taskType = DownloadTaskType.Audio,
                                        taskLabel = downloadTitle,
                                        mediaParams = mediaParams,
                                        formatLabel = mapOutputExtensionLabel(audioExtension),
                                    )
                                    val audioName = resolveTemplateFileName(
                                        taskType = DownloadTaskType.Audio,
                                        namingSession = namingSession,
                                        context = audioNamingContext,
                                        extension = audioExtension,
                                    )
                                    lastDownload = downloadRepository.enqueue(
                                        groupId,
                                        DownloadTaskType.Audio,
                                        downloadTitle,
                                        audioName,
                                        selectedAudio!!.url,
                                        mediaParams,
                                        embeddedMetadata = embeddedMetadata,
                                    )
                                }
                                OutputType.VideoOnly -> {
                                    val mediaParams = buildMediaParams(selectedVideo, outputVideoCodec, null)
                                    val videoNamingContext = buildNamingRenderContext(
                                        info = info,
                                        item = item,
                                        naming = naming,
                                        namingSession = namingSession,
                                        taskType = DownloadTaskType.Video,
                                        taskLabel = downloadTitle,
                                        mediaParams = mediaParams,
                                        formatLabel = mapStreamFormatLabel(selectedVideo!!.format),
                                    )
                                    val videoName = resolveTemplateFileName(
                                        taskType = DownloadTaskType.Video,
                                        namingSession = namingSession,
                                        context = videoNamingContext,
                                        extension = extensionForVideoStream(selectedVideo!!),
                                    )
                                    lastDownload = downloadRepository.enqueue(
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
                                        val mergedExtension = extensionForMergedOutput(selectedAudio!!)
                                        val mergedNamingContext = buildNamingRenderContext(
                                            info = info,
                                            item = item,
                                            naming = naming,
                                            namingSession = namingSession,
                                            taskType = DownloadTaskType.AudioVideo,
                                            taskLabel = downloadTitle,
                                            mediaParams = mediaParams,
                                            formatLabel = mapOutputExtensionLabel(mergedExtension),
                                        )
                                        val outputName = resolveTemplateFileName(
                                            taskType = DownloadTaskType.AudioVideo,
                                            namingSession = namingSession,
                                            context = mergedNamingContext,
                                            extension = mergedExtension,
                                        )
                                        lastDownload = downloadRepository.enqueueDashMerge(
                                            groupId,
                                            downloadTitle,
                                            outputName,
                                            mergeVideo!!.url,
                                            selectedAudio!!.url,
                                            mediaParams,
                                            embeddedMetadata = embeddedMetadata,
                                        )
                                    } else {
                                        val mediaParams = buildMediaParams(mergeVideo, outputVideoCodec, selectedAudio)
                                        val mergedNamingContext = buildNamingRenderContext(
                                            info = info,
                                            item = item,
                                            naming = naming,
                                            namingSession = namingSession,
                                            taskType = DownloadTaskType.AudioVideo,
                                            taskLabel = downloadTitle,
                                            mediaParams = mediaParams,
                                            formatLabel = mapStreamFormatLabel(mergeVideo!!.format),
                                        )
                                        val videoName = resolveTemplateFileName(
                                            taskType = DownloadTaskType.AudioVideo,
                                            namingSession = namingSession,
                                            context = mergedNamingContext,
                                            extension = extensionForVideoStream(mergeVideo!!),
                                        )
                                        lastDownload = downloadRepository.enqueue(
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
                        }
                    }

                    launchExtraTasksForItem(
                        snapshot = snapshot,
                        info = info,
                        item = item,
                        naming = naming,
                        namingSession = namingSession,
                        groupId = groupId,
                        groupRelativePath = groupRelativePath,
                    )
                }
                _state.update { it.copy(lastDownload = lastDownload) }
            }
        }
    }

    private fun launchExtraTasksForItem(
        snapshot: ParseUiState,
        info: MediaInfo,
        item: MediaItem,
        naming: GroupNaming,
        namingSession: NamingSession,
        groupId: Long,
        groupRelativePath: String,
    ) {
        if (snapshot.subtitleEnabled) {
            val aid = item.aid
            val cid = item.cid
            val subtitleTitle = strings.get(R.string.parse_subtitle_label)
            if (aid == null || cid == null) {
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.Subtitle,
                    subtitleTitle,
                    strings.get(R.string.parse_error_no_subtitle),
                )
            } else {
                val subtitleContext = buildNamingRenderContext(
                    info = info,
                    item = item,
                    naming = naming,
                    namingSession = namingSession,
                    taskType = DownloadTaskType.Subtitle,
                    taskLabel = subtitleTitle,
                    mediaParams = null,
                )
                val initialName = resolveTemplateFileName(
                    taskType = DownloadTaskType.Subtitle,
                    namingSession = namingSession,
                    context = subtitleContext,
                    extension = snapshot.selectedSubtitleLan?.let { "$it.srt" } ?: "srt",
                )
                saveBytesTask(
                    groupId = groupId,
                    type = DownloadTaskType.Subtitle,
                    taskTitle = subtitleTitle,
                    fileName = initialName,
                    mimeType = null,
                    relativePath = groupRelativePath,
                    bytesProvider = { _, _, updateMetadata ->
                        val subtitles = extrasRepository.getSubtitles(aid, cid)
                        val subtitle = selectSubtitle(subtitles, snapshot.selectedSubtitleLan)
                            ?: return@saveBytesTask null
                        val name = resolveTemplateFileName(
                            taskType = DownloadTaskType.Subtitle,
                            namingSession = namingSession,
                            context = subtitleContext,
                            extension = "${subtitle.lan}.srt",
                        )
                        updateMetadata(
                            "${subtitleTitle} - ${subtitle.name}",
                            name,
                        )
                        extrasRepository.getSubtitleSrt(subtitle)
                    },
                    errorMessage = strings.get(R.string.parse_error_no_subtitle),
                )
            }
        }

        if (snapshot.aiSummaryEnabled) {
            val aid = item.aid
            val cid = item.cid
            val bvid = item.bvid
            val taskTitle = strings.get(R.string.parse_ai_summary_label)
            if (aid == null || cid == null || bvid.isNullOrBlank()) {
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.AiSummary,
                    taskTitle,
                    strings.get(R.string.parse_error_no_ai),
                )
            } else {
                val summaryTitle = info.nfo.showTitle?.ifBlank { item.title } ?: item.title
                val aiSummaryContext = buildNamingRenderContext(
                    info = info,
                    item = item,
                    naming = naming,
                    namingSession = namingSession,
                    taskType = DownloadTaskType.AiSummary,
                    taskLabel = taskTitle,
                    mediaParams = null,
                )
                val name = resolveTemplateFileName(
                    taskType = DownloadTaskType.AiSummary,
                    namingSession = namingSession,
                    context = aiSummaryContext,
                    extension = "md",
                )
                saveTextTask(
                    groupId = groupId,
                    type = DownloadTaskType.AiSummary,
                    taskTitle = taskTitle,
                    fileName = name,
                    mimeType = null,
                    relativePath = groupRelativePath,
                    contentProvider = {
                        extrasRepository.getAiSummaryMarkdown(summaryTitle, bvid, aid, cid)
                    },
                    errorMessage = strings.get(R.string.parse_error_no_ai),
                )
            }
        }

        if (snapshot.nfoCollectionEnabled) {
            val taskTitle = strings.get(R.string.parse_nfo_collection)
            if (isCollectionNfoAvailable(info)) {
                saveTextTask(
                    groupId = groupId,
                    type = DownloadTaskType.NfoCollection,
                    taskTitle = taskTitle,
                    fileName = "tvshow.nfo",
                    mimeType = null,
                    relativePath = groupRelativePath,
                    contentProvider = { NfoGenerator.buildCollectionNfo(info) },
                    errorMessage = strings.get(R.string.parse_error_no_nfo),
                )
            } else {
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.NfoCollection,
                    taskTitle,
                    strings.get(R.string.parse_error_no_nfo),
                )
            }
        }

        if (snapshot.nfoSingleEnabled) {
            val taskTitle = strings.get(R.string.parse_nfo_single)
            val nfoContext = buildNamingRenderContext(
                info = info,
                item = item,
                naming = naming,
                namingSession = namingSession,
                taskType = DownloadTaskType.NfoSingle,
                taskLabel = taskTitle,
                mediaParams = null,
            )
            val name = resolveTemplateFileName(
                taskType = DownloadTaskType.NfoSingle,
                namingSession = namingSession,
                context = nfoContext,
                extension = "nfo",
            )
            saveTextTask(
                groupId = groupId,
                type = DownloadTaskType.NfoSingle,
                taskTitle = taskTitle,
                fileName = name,
                mimeType = null,
                relativePath = groupRelativePath,
                contentProvider = { NfoGenerator.buildSingleNfo(info, item) },
                errorMessage = strings.get(R.string.parse_error_no_nfo),
            )
        }

        val convertXmlDanmakuToAss = settingsRepository.shouldConvertXmlDanmakuToAss()
        if (snapshot.danmakuLiveEnabled) {
            val aid = item.aid
            val cid = item.cid
            val duration = item.duration
            val taskTitle = strings.get(R.string.parse_danmaku_live)
            val danmakuLiveContext = buildNamingRenderContext(
                info = info,
                item = item,
                naming = naming,
                namingSession = namingSession,
                taskType = DownloadTaskType.DanmakuLive,
                taskLabel = taskTitle,
                mediaParams = null,
            )
            val name = resolveTemplateFileName(
                taskType = DownloadTaskType.DanmakuLive,
                namingSession = namingSession,
                context = danmakuLiveContext,
                extension = if (convertXmlDanmakuToAss) "ass" else "xml",
            )
            if (aid == null || cid == null) {
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.DanmakuLive,
                    taskTitle,
                    strings.get(R.string.parse_error_no_danmaku),
                )
            } else {
                saveBytesTask(
                    groupId = groupId,
                    type = DownloadTaskType.DanmakuLive,
                    taskTitle = taskTitle,
                    fileName = name,
                    mimeType = null,
                    relativePath = groupRelativePath,
                    bytesProvider = { _, onProgress, _ ->
                        if (convertXmlDanmakuToAss) {
                            extrasRepository.getDanmakuLiveAss(aid, cid, duration) { progress ->
                                onProgress(danmakuLiveProgressUpdate(progress))
                            }
                        } else {
                            extrasRepository.getDanmakuLiveXml(aid, cid, duration) { progress ->
                                onProgress(danmakuLiveProgressUpdate(progress))
                            }
                        }
                    },
                    errorMessage = strings.get(R.string.common_error_unknown),
                )
            }
        }

        if (snapshot.danmakuHistoryEnabled) {
            val date = snapshot.danmakuDate
            val hour = parseHour(snapshot.danmakuHour)
            val taskTitle = strings.get(R.string.parse_danmaku_history)
            val cid = item.cid
            val validationError = when {
                cid == null -> strings.get(R.string.parse_error_no_danmaku)
                !isValidDate(date) -> strings.get(R.string.parse_error_invalid_date)
                snapshot.danmakuHour.isNotBlank() && hour == null ->
                    strings.get(R.string.parse_error_invalid_hour)
                else -> null
            }
            if (validationError != null) {
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.DanmakuHistory,
                    taskTitle,
                    validationError,
                )
            } else {
                val historyCid = cid!!
                val danmakuHistoryContext = buildNamingRenderContext(
                    info = info,
                    item = item,
                    naming = naming,
                    namingSession = namingSession,
                    taskType = DownloadTaskType.DanmakuHistory,
                    taskLabel = taskTitle,
                    mediaParams = null,
                )
                val name = resolveTemplateFileName(
                    taskType = DownloadTaskType.DanmakuHistory,
                    namingSession = namingSession,
                    context = danmakuHistoryContext,
                    extension = if (convertXmlDanmakuToAss) "ass" else "xml",
                )
                saveBytesTask(
                    groupId = groupId,
                    type = DownloadTaskType.DanmakuHistory,
                    taskTitle = taskTitle,
                    fileName = name,
                    mimeType = null,
                    relativePath = groupRelativePath,
                    bytesProvider = { _, _, _ ->
                        if (convertXmlDanmakuToAss) {
                            extrasRepository.getDanmakuHistoryAss(historyCid, date, hour)
                        } else {
                            extrasRepository.getDanmakuHistoryXml(historyCid, date, hour)
                        }
                    },
                    errorMessage = strings.get(R.string.common_error_unknown),
                )
            }
        }

        val selectedIds = snapshot.selectedImageIds
        if (selectedIds.isNotEmpty()) {
            val thumbs = info.nfo.thumbs
                .filter { it.url.isNotBlank() }
                .filter { selectedIds.contains(it.id) }
                .distinctBy { it.id }
            if (thumbs.isNotEmpty()) {
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
                    val taskType = when (thumb.id) {
                        "cover", "pic" -> DownloadTaskType.Cover
                        else -> DownloadTaskType.CollectionCover
                    }
                    val imageContext = buildNamingRenderContext(
                        info = info,
                        item = item,
                        naming = naming,
                        namingSession = namingSession,
                        taskType = taskType,
                        taskLabel = fileLabel,
                        mediaParams = null,
                    )
                    val name = resolveTemplateFileName(
                        taskType = taskType,
                        namingSession = namingSession,
                        context = imageContext,
                        extension = extensionFromUrl(thumb.url),
                    )
                    saveBytesTask(
                        groupId = groupId,
                        type = taskType,
                        taskTitle = label,
                        fileName = name,
                        mimeType = "image/*",
                        relativePath = groupRelativePath,
                        bytesProvider = { _, _, _ -> extrasRepository.fetchBytes(thumb.url) },
                        errorMessage = strings.get(R.string.common_error_unknown),
                    )
                }
            } else {
                val message = strings.get(R.string.parse_error_no_cover)
                addFailedExtraTask(
                    groupId,
                    DownloadTaskType.CollectionCover,
                    strings.get(R.string.parse_image_label),
                    message,
                )
            }
        }
    }

    fun copySubtitlesNow() {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (snapshot.subtitleCopying || snapshot.aiSummaryCopying) return
        val selectedIndices = snapshot.selectedItemIndices.filter { it in snapshot.items.indices }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
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
                _events.emit(ParseEvent.CopySingleSubtitle(entry))
                return@launch
            }
            _events.emit(ParseEvent.ShowSubtitleCopyDialog(entries))
        }
    }

    fun copyAiSummariesNow() {
        val snapshot = _state.value
        val info = snapshot.mediaInfo ?: return
        if (snapshot.subtitleCopying || snapshot.aiSummaryCopying) return
        val selectedIndices = snapshot.selectedItemIndices.filter { it in snapshot.items.indices }
        if (selectedIndices.isEmpty()) {
            _state.update { it.copy(error = strings.get(R.string.parse_error_no_selection)) }
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
                _events.emit(ParseEvent.CopySingleAiSummary(entry))
                return@launch
            }
            _events.emit(ParseEvent.ShowAiSummaryCopyDialog(entries))
        }
    }

    private suspend fun buildSubtitleCopyEntries(
        snapshot: ParseUiState,
        info: MediaInfo,
        selectedIndices: List<Int>,
    ): List<SubtitleCopyEntry> {
        val targets = buildDownloadTargets(snapshot, info, selectedIndices)
        return targets.items.map { rawItem ->
            val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                .getOrDefault(rawItem)
            val naming = resolveGroupNaming(
                info = info,
                item = item,
                isCollectionMode = targets.isCollectionMode,
                pageCountByBvid = targets.pageCountByBvid,
                videoTitleByBvid = targets.videoTitleByBvid,
            )
            val aid = item.aid
            val cid = item.cid
            val subtitles = if (aid != null && cid != null) {
                runCatching { extrasRepository.getSubtitles(aid, cid) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val subtitle = selectSubtitle(subtitles, snapshot.selectedSubtitleLan)
            val title = buildSubtitleEntryTitle(naming.groupTitle, naming.groupSubtitle)
            if (subtitle == null) {
                SubtitleCopyEntry(
                    title = title,
                    subtitleName = null,
                    content = null,
                    error = strings.get(R.string.parse_error_no_subtitle),
                )
            } else {
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

    private suspend fun buildAiSummaryCopyEntries(
        snapshot: ParseUiState,
        info: MediaInfo,
        selectedIndices: List<Int>,
    ): List<AiSummaryCopyEntry> {
        val targets = buildDownloadTargets(snapshot, info, selectedIndices)
        return targets.items.map { rawItem ->
            val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                .getOrDefault(rawItem)
            val naming = resolveGroupNaming(
                info = info,
                item = item,
                isCollectionMode = targets.isCollectionMode,
                pageCountByBvid = targets.pageCountByBvid,
                videoTitleByBvid = targets.videoTitleByBvid,
            )
            val title = buildSubtitleEntryTitle(naming.groupTitle, naming.groupSubtitle)
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
    ): DownloadTargets {
        val isCollectionMode = snapshot.collectionMode && info.type == MediaType.Video
        val pageCountByBvid = mutableMapOf<String, Int>()
        val videoTitleByBvid = mutableMapOf<String, String>()
        val collectionTitlesByBvid = if (!isCollectionMode &&
            info.type == MediaType.Video &&
            info.collection
        ) {
            runCatching {
                mediaRepository.getMediaInfo(
                    info.id,
                    info.type,
                    com.happycola233.bilitools.data.model.MediaQueryOptions(collection = true),
                ).list.mapNotNull { item ->
                    val bvid = item.bvid?.trim().orEmpty()
                    val title = item.title.trim()
                    if (bvid.isNotBlank() && title.isNotBlank()) bvid to title else null
                }.toMap()
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        val defaultVideoTitle = if (info.type == MediaType.Video) {
            val sectionTitle = info.sections?.let { sections ->
                sections.tabs.firstOrNull { it.id == sections.target }?.name
            }?.trim().orEmpty()
            when {
                sectionTitle.isNotBlank() -> sectionTitle
                !info.collection -> info.nfo.showTitle?.trim().orEmpty()
                else -> ""
            }
        } else {
            ""
        }
        val selectedItems = selectedIndices.mapNotNull { snapshot.items.getOrNull(it) }
        val items = if (isCollectionMode) {
            selectedItems.flatMap { episode ->
                val pages = runCatching { mediaRepository.getVideoPages(episode) }
                    .getOrDefault(listOf(episode))
                val episodeTitle = episode.title.trim().ifBlank { defaultVideoTitle }
                val episodeBvid = episode.bvid?.trim().orEmpty()
                if (episodeBvid.isNotBlank()) {
                    pageCountByBvid[episodeBvid] = pages.size
                    if (episodeTitle.isNotBlank()) {
                        videoTitleByBvid[episodeBvid] = episodeTitle
                    }
                }
                if (pages.size <= 1) {
                    val page = pages.firstOrNull() ?: episode
                    listOf(page.copy(title = episode.title))
                } else {
                    pages.map { page ->
                        val title = page.title.ifBlank { episode.title }
                        page.copy(title = title)
                    }
                }
            }
        } else {
            if (info.type == MediaType.Video) {
                val fallbackTitle = if (defaultVideoTitle.isNotBlank()) {
                    defaultVideoTitle
                } else {
                    snapshot.items.firstOrNull()?.title?.trim().orEmpty()
                }
                snapshot.items
                    .asSequence()
                    .mapNotNull { item -> item.bvid?.trim()?.takeIf { it.isNotBlank() } }
                    .distinct()
                    .forEach { bvid ->
                        pageCountByBvid[bvid] = snapshot.items.count { it.bvid == bvid }
                        val mappedTitle = collectionTitlesByBvid[bvid].orEmpty()
                        val finalTitle = when {
                            mappedTitle.isNotBlank() -> mappedTitle
                            fallbackTitle.isNotBlank() -> fallbackTitle
                            else -> ""
                        }
                        if (finalTitle.isNotBlank()) {
                            videoTitleByBvid[bvid] = finalTitle
                        }
                    }
            }
            selectedItems
        }
        return DownloadTargets(
            isCollectionMode = isCollectionMode,
            items = items,
            pageCountByBvid = pageCountByBvid,
            videoTitleByBvid = videoTitleByBvid,
        )
    }

    private fun resolveGroupNaming(
        info: MediaInfo,
        item: MediaItem,
        isCollectionMode: Boolean,
        pageCountByBvid: Map<String, Int>,
        videoTitleByBvid: Map<String, String>,
    ): GroupNaming {
        val parentTitle = info.nfo.showTitle?.trim().orEmpty()
        val itemTitle = item.title.trim()
        val groupTitle: String
        val groupSubtitle: String?
        if (isCollectionMode) {
            groupTitle = when {
                itemTitle.isNotBlank() -> itemTitle
                parentTitle.isNotBlank() -> parentTitle
                else -> "BiliTools"
            }
            groupSubtitle = null
        } else {
            val useParentTitle =
                (info.type == MediaType.Video && !info.collection) ||
                    info.type == MediaType.Bangumi ||
                    info.type == MediaType.Lesson
            groupTitle = when {
                useParentTitle && parentTitle.isNotBlank() -> parentTitle
                itemTitle.isNotBlank() -> itemTitle
                parentTitle.isNotBlank() -> parentTitle
                else -> "BiliTools"
            }
            groupSubtitle = when {
                useParentTitle && parentTitle.isNotBlank() && itemTitle.isNotBlank() &&
                    itemTitle != parentTitle -> itemTitle
                !useParentTitle && parentTitle.isNotBlank() && parentTitle != itemTitle -> parentTitle
                else -> null
            }
        }
        val bvid = item.bvid?.trim().orEmpty()
        val pageCount = if (bvid.isNotBlank()) pageCountByBvid[bvid] ?: 0 else 0
        val videoTitle = if (bvid.isNotBlank()) videoTitleByBvid[bvid].orEmpty() else ""
        val useVideoNaming = info.type == MediaType.Video && videoTitle.isNotBlank()
        val pageName = if (useVideoNaming && pageCount > 1) {
            "${videoTitle} - P${item.index + 1}"
        } else if (useVideoNaming) {
            videoTitle
        } else {
            ""
        }
        val effectiveGroupTitle = if (useVideoNaming) pageName else groupTitle
        val effectiveGroupSubtitle = if (useVideoNaming) null else groupSubtitle
        val baseNameSource = if (effectiveGroupSubtitle.isNullOrBlank()) {
            effectiveGroupTitle
        } else {
            "$effectiveGroupTitle-$effectiveGroupSubtitle"
        }
        return GroupNaming(
            groupTitle = effectiveGroupTitle,
            groupSubtitle = effectiveGroupSubtitle,
            baseName = sanitizeFileName(baseNameSource.ifBlank { "BiliTools" }),
            useVideoNaming = useVideoNaming,
            pageCount = pageCount,
            videoTitle = videoTitle,
        )
    }

    private fun resolveNamingCollectionTitle(info: MediaInfo): String? {
        val parentTitle = info.nfo.showTitle?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (info.type) {
            MediaType.Video -> parentTitle.takeIf { info.collection }
            MediaType.Bangumi,
            MediaType.Lesson,
            MediaType.MusicList,
            MediaType.Favorite,
            MediaType.OpusList,
            MediaType.UserVideo,
            -> parentTitle
            else -> null
        }
    }

    private fun resolveNamingVideoTitle(
        info: MediaInfo,
        item: MediaItem?,
        groupedVideoTitle: String? = null,
    ): String? {
        val resolvedGroupedTitle = groupedVideoTitle?.trim()?.takeIf { it.isNotBlank() }
        if (resolvedGroupedTitle != null) {
            return resolvedGroupedTitle
        }
        if (info.type == MediaType.Video && !info.collection) {
            info.nfo.showTitle?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        item?.title?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        return when (info.type) {
            MediaType.Music,
            MediaType.Opus,
            -> info.nfo.showTitle?.trim()?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun createNamingSession(
        info: MediaInfo,
        targets: DownloadTargets,
    ): NamingSession {
        val namingSettings = settingsRepository.currentNamingSettings()
        val downTimeEpochSeconds = System.currentTimeMillis() / 1000L
        val baseNamingSession = NamingSession(
            useTopLevelFolder = false,
            topLevelFolderTemplate = namingSettings.topLevelFolderTemplate,
            itemFolderTemplate = namingSettings.itemFolderTemplate,
            fileTemplate = namingSettings.fileTemplate,
            cleanSeparators = namingSettings.cleanSeparators,
            downTimeEpochSeconds = downTimeEpochSeconds,
            topLevelFolderName = null,
        )
        val itemFolderCount = targets.items
            .map { item ->
                val naming = resolveGroupNaming(
                    info = info,
                    item = item,
                    isCollectionMode = targets.isCollectionMode,
                    pageCountByBvid = targets.pageCountByBvid,
                    videoTitleByBvid = targets.videoTitleByBvid,
                )
                val context = buildNamingRenderContext(
                    info = info,
                    item = item,
                    naming = naming,
                    namingSession = baseNamingSession,
                    taskType = null,
                    taskLabel = null,
                    mediaParams = null,
                )
                DownloadNaming.renderComponent(
                    template = namingSettings.itemFolderTemplate,
                    context = context,
                    cleanSeparators = namingSettings.cleanSeparators,
                )
            }
            .filter { it.isNotBlank() }
            .distinct()
            .size
        val useTopLevelFolder = when (namingSettings.topLevelFolderMode) {
            TopLevelFolderMode.Auto -> itemFolderCount > 1
            TopLevelFolderMode.Enabled -> true
            TopLevelFolderMode.Disabled -> false
        }
        val representativeItem = targets.items.firstOrNull()
        val representativeGroupedTitle = representativeItem?.bvid
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { targets.videoTitleByBvid[it] }
        val videoTitle = resolveNamingVideoTitle(
            info = info,
            item = representativeItem,
            groupedVideoTitle = representativeGroupedTitle,
        )
        val collectionTitle = resolveNamingCollectionTitle(info)
        val containerLabel = mapMediaTypeLabel(info.type)
        val topContext = NamingRenderContext(
            videoTitle = videoTitle,
            collectionTitle = collectionTitle,
            container = containerLabel,
            pubTimeEpochSeconds = representativeItem?.pubTime?.takeIf { it > 0L }
                ?: info.nfo.premiered,
            downTimeEpochSeconds = downTimeEpochSeconds,
            upper = info.nfo.upper?.name,
            upperId = info.nfo.upper?.mid?.toString(),
            aid = representativeItem?.aid?.toString(),
            sid = representativeItem?.sid?.toString(),
            fid = representativeItem?.fid?.toString(),
            cid = representativeItem?.cid?.toString(),
            bvid = representativeItem?.bvid,
            epid = representativeItem?.epid?.toString(),
            ssid = representativeItem?.ssid?.toString(),
            opid = representativeItem?.opid,
        )
        val topLevelFolderName = if (useTopLevelFolder) {
            DownloadNaming.renderComponent(
                template = namingSettings.topLevelFolderTemplate,
                context = topContext,
                cleanSeparators = namingSettings.cleanSeparators,
            )
        } else {
            null
        }
        return baseNamingSession.copy(
            useTopLevelFolder = useTopLevelFolder,
            topLevelFolderName = topLevelFolderName,
        )
    }

    private fun buildRequestedGroupRelativePath(
        info: MediaInfo,
        item: MediaItem,
        naming: GroupNaming,
        namingSession: NamingSession,
    ): String {
        val context = buildNamingRenderContext(
            info = info,
            item = item,
            naming = naming,
            namingSession = namingSession,
            taskType = null,
            taskLabel = null,
            mediaParams = null,
        )
        val itemFolderName = DownloadNaming.renderComponent(
            template = namingSession.itemFolderTemplate,
            context = context,
            cleanSeparators = namingSession.cleanSeparators,
        )
        val segments = buildList {
            add(settingsRepository.downloadRootRelativePath().replace('\\', '/').trim().trim('/'))
            if (namingSession.useTopLevelFolder) {
                namingSession.topLevelFolderName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
            add(itemFolderName)
        }.filter { it.isNotBlank() }
        return segments.joinToString("/")
    }

    private fun buildNamingRenderContext(
        info: MediaInfo,
        item: MediaItem,
        naming: GroupNaming,
        namingSession: NamingSession,
        taskType: DownloadTaskType?,
        taskLabel: String?,
        mediaParams: DownloadMediaParams?,
        formatLabel: String? = null,
    ): NamingRenderContext {
        val containerLabel = mapMediaTypeLabel(info.type)
        val mediaTypeLabel = mapMediaTypeLabel(item.type)
        val videoTitle = resolveNamingVideoTitle(
            info = info,
            item = item,
            groupedVideoTitle = naming.videoTitle,
        )
        val collectionTitle = resolveNamingCollectionTitle(info)
        val resolvedTaskLabel = taskLabel ?: taskType?.let(::mapTaskTypeLabel)
        return NamingRenderContext(
            videoTitle = videoTitle,
            collectionTitle = collectionTitle,
            title = item.title.trim().takeIf { it.isNotBlank() },
            p = if (naming.useVideoNaming) {
                (item.index + 1).toString()
            } else {
                null
            },
            container = containerLabel,
            mediaType = mediaTypeLabel,
            taskType = resolvedTaskLabel,
            index = item.index + 1,
            pubTimeEpochSeconds = item.pubTime.takeIf { it > 0L } ?: info.nfo.premiered,
            downTimeEpochSeconds = namingSession.downTimeEpochSeconds,
            upper = info.nfo.upper?.name,
            upperId = info.nfo.upper?.mid?.toString(),
            aid = item.aid?.toString(),
            sid = item.sid?.toString(),
            fid = item.fid?.toString(),
            cid = item.cid?.toString(),
            bvid = item.bvid?.trim()?.takeIf { it.isNotBlank() },
            epid = item.epid?.toString(),
            ssid = item.ssid?.toString(),
            opid = item.opid?.trim()?.takeIf { it.isNotBlank() },
            res = mediaParams?.resolution,
            abr = mediaParams?.audioBitrate,
            enc = mediaParams?.codec,
            fmt = formatLabel,
        )
    }

    private fun resolveTemplateFileName(
        taskType: DownloadTaskType,
        namingSession: NamingSession,
        context: NamingRenderContext,
        extension: String,
    ): String {
        val baseName = DownloadNaming.renderComponent(
            template = namingSession.fileTemplate,
            context = context,
            cleanSeparators = namingSession.cleanSeparators,
        )
        return DownloadNaming.appendExtension(
            baseName = baseName,
            extension = extension,
            cleanSeparators = namingSession.cleanSeparators,
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

    private fun selectSubtitle(subtitles: List<SubtitleInfo>, selectedLan: String?): SubtitleInfo? {
        return subtitles.firstOrNull { it.lan == selectedLan }
            ?: if (selectedLan == null) subtitles.firstOrNull() else null
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
        val aid = item.aid
        val cid = item.cid
        val subtitles = if (aid != null && cid != null) {
            runCatching { extrasRepository.getSubtitles(aid, cid) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val aiAvailable = if (aid != null && cid != null) {
            runCatching { extrasRepository.hasAiSummary(aid, cid) }.getOrDefault(false)
        } else {
            false
        }
        val selectedSubtitle = pickSubtitle(subtitles, _state.value.selectedSubtitleLan)
        val collectionAvailable = isCollectionNfoAvailable(info)
        val thumbs = info.nfo.thumbs.filter { it.url.isNotBlank() }
        val imageOptions = thumbs
            .distinctBy { it.id }
            .map { thumb -> ImageOption(thumb.id, mapImageLabel(thumb.id)) }
        val imageOptionIds = imageOptions.map { it.id }
        val imageOptionIdSet = imageOptionIds.toSet()
        val selectedImageIds =
            _state.value.selectedImageIds.filter { imageOptionIdSet.contains(it) }.toSet()
        val allowMissing = _state.value.selectedItemIndices.size > 1
        _state.update {
            it.copy(
                subtitleList = subtitles,
                selectedSubtitleLan = selectedSubtitle,
                subtitleEnabled = if (allowMissing) {
                    it.subtitleEnabled
                } else {
                    it.subtitleEnabled && subtitles.isNotEmpty()
                },
                aiSummaryAvailable = aiAvailable,
                aiSummaryEnabled = if (allowMissing) {
                    it.aiSummaryEnabled
                } else if (aiAvailable) {
                    it.aiSummaryEnabled
                } else {
                    false
                },
                nfoCollectionEnabled = if (allowMissing) {
                    it.nfoCollectionEnabled
                } else {
                    it.nfoCollectionEnabled && collectionAvailable
                },
                danmakuLiveEnabled = if (allowMissing) {
                    it.danmakuLiveEnabled
                } else {
                    it.danmakuLiveEnabled && aid != null && cid != null
                },
                danmakuHistoryEnabled = if (allowMissing) {
                    it.danmakuHistoryEnabled
                } else {
                    it.danmakuHistoryEnabled && cid != null
                },
                imageOptions = imageOptions,
                selectedImageIds = selectedImageIds,
            )
        }
        refreshSelectedItemPresentation(info, item)
    }

    private fun refreshSelectedItemPresentation(info: MediaInfo, item: MediaItem) {
        val itemKey = itemCacheKey(item)
        val cachedStat = itemKey?.let { itemStatCache[it] } ?: item.stat
        val cachedDescription = itemKey?.let { itemDescriptionCache[it] }
        val itemDescription = item.description.trim()

        if (cachedStat != null || !cachedDescription.isNullOrBlank()) {
            applySelectedItemPresentation(itemKey, cachedStat, cachedDescription)
        }

        if (!shouldFetchPresentationDetail(info, item) || itemKey == null) return
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
            applySelectedItemPresentation(itemKey, detail.stat, detail.description)
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

    private fun applySelectedItemPresentation(
        itemKey: String?,
        stat: MediaStat?,
        description: String?,
    ) {
        _state.update { current ->
            val currentIndex = current.selectedItemIndex
            val currentItem = current.items.getOrNull(currentIndex) ?: return@update current
            if (itemKey != null && itemCacheKey(currentItem) != itemKey) {
                return@update current
            }

            var changed = false
            var updatedItem = currentItem

            if (!description.isNullOrBlank() && description != currentItem.description) {
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
                    list[currentIndex] = updatedItem
                }
            } else {
                current.items
            }

            val nextSelectedItemStat = resolvedStat ?: current.selectedItemStat
            if (!changed && nextSelectedItemStat == current.selectedItemStat) {
                return@update current
            }

            current.copy(
                items = updatedItems,
                selectedItemStat = nextSelectedItemStat,
            )
        }
    }

    private fun shouldFetchPresentationDetail(info: MediaInfo, item: MediaItem): Boolean {
        return when (info.type) {
            MediaType.Favorite -> item.type == MediaType.Video && item.bvid?.isNotBlank() == true
            MediaType.WatchLater -> item.type == MediaType.Video &&
                (item.bvid?.isNotBlank() == true || item.aid != null)
            else -> false
        }
    }

    private fun shouldFetchPresentationStatDetail(info: MediaInfo, stat: MediaStat?): Boolean {
        if (stat == null) return true
        return when (info.type) {
            MediaType.Favorite -> stat.reply == null ||
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
        if (
            loading ||
            collectionModeLoading ||
            mediaInfo == null ||
            outputType == null ||
            selectedItemIndices.isEmpty()
        ) {
            return null
        }
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
        val artist = info.nfo.upper?.name?.trim()?.takeIf { it.isNotBlank() }
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

    private fun addFailedExtraTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        errorMessage: String,
        fileName: String = "",
    ) {
        downloadRepository.addExtraTask(
            groupId,
            type,
            taskTitle,
            fileName,
            DownloadStatus.Failed,
            errorMessage = errorMessage,
        )
        _state.update { it.copy(error = errorMessage) }
    }

    private fun isCollectionNfoAvailable(info: MediaInfo): Boolean {
        if (info.nfo.showTitle.isNullOrBlank()) return false
        return info.collection ||
            info.type == MediaType.Bangumi ||
            info.type == MediaType.Lesson
    }

    private fun danmakuLiveProgressUpdate(progress: DanmakuLiveProgress): ExtraTaskProgress {
        val segmentCount = progress.segmentCount.coerceAtLeast(1)
        return when (progress.phase) {
            DanmakuLiveProgressPhase.FetchingSegment -> ExtraTaskProgress(
                progress = progress.progress,
                statusDetail = strings.get(
                    R.string.download_detail_fetching_danmaku_segment,
                    progress.segmentIndex.coerceIn(1, segmentCount),
                    segmentCount,
                ),
            )
            DanmakuLiveProgressPhase.Converting -> ExtraTaskProgress(
                progress = progress.progress,
                statusDetail = strings.get(R.string.download_detail_converting_danmaku),
                progressIndeterminate = true,
            )
        }
    }

    private fun extraTaskErrorMessage(fallback: String, err: Throwable?): String {
        if (err == null) return fallback
        val detail = when (err) {
            is BiliHttpException -> {
                val base = err.message?.takeIf { it.isNotBlank() }
                    ?: strings.get(R.string.parse_error_failed)
                "$base (${err.code})"
            }
            else -> err.message
        }?.takeIf { it.isNotBlank() }
        return if (detail == null) fallback else "$fallback: $detail"
    }

    private fun saveTextTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        mimeType: String?,
        relativePath: String,
        contentProvider: suspend () -> String?,
        errorMessage: String,
    ) {
        val task = downloadRepository.addExtraTask(
            groupId,
            type,
            taskTitle,
            fileName,
            DownloadStatus.Pending,
        )
        downloadRepository.launchExtraTask(task.id) extraTask@{
            try {
                if (!downloadRepository.updateExtraTask(task.id, DownloadStatus.Running, progress = 0)) {
                    return@extraTask
                }
                val content = contentProvider()
                if (content.isNullOrBlank()) {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Failed,
                        progress = 0,
                        errorMessage = errorMessage,
                    )
                    _state.update { it.copy(error = errorMessage) }
                    return@extraTask
                }
                if (!downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Running,
                        progress = 99,
                        statusDetail = strings.get(R.string.download_detail_saving_file),
                    )
                ) {
                    return@extraTask
                }
                val bytes = content.toByteArray(Charsets.UTF_8)
                val byteCount = bytes.size.toLong()
                val uri = exportRepository.saveBytes(fileName, mimeType, bytes, relativePath)
                if (uri != null) {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Success,
                        progress = 100,
                        downloadedBytes = byteCount,
                        totalBytes = byteCount,
                        localUri = uri.toString(),
                    )
                } else {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Failed,
                        progress = 0,
                        errorMessage = errorMessage,
                    )
                    _state.update { it.copy(error = errorMessage) }
                }
            } catch (err: CancellationException) {
                downloadRepository.updateExtraTask(
                    task.id,
                    DownloadStatus.Cancelled,
                    progress = 0,
                    errorMessage = err.message,
                )
                throw err
            } catch (err: Throwable) {
                val message = extraTaskErrorMessage(errorMessage, err)
                AppLog.w(TAG, "[extra-task] text failed, type=$type, file=$fileName", err)
                downloadRepository.updateExtraTask(
                    task.id,
                    DownloadStatus.Failed,
                    progress = 0,
                    errorMessage = message,
                )
                _state.update { it.copy(error = message) }
            }
        }
    }

    private fun saveBytesTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        mimeType: String?,
        relativePath: String,
        bytesProvider: suspend (
            DownloadItem,
            (ExtraTaskProgress) -> Unit,
            (String, String) -> Unit,
        ) -> ByteArray?,
        errorMessage: String,
    ) {
        val task = downloadRepository.addExtraTask(
            groupId,
            type,
            taskTitle,
            fileName,
            DownloadStatus.Pending,
        )
        downloadRepository.launchExtraTask(task.id) extraTask@{
            try {
                var outputFileName = fileName
                var lastProgress = 0
                var lastStatusDetail: String? = null
                var lastProgressIndeterminate = false
                fun updateRunningProgress(update: ExtraTaskProgress) {
                    val normalized = update.progress.coerceIn(0, 99)
                    val statusDetail = update.statusDetail?.takeIf { it.isNotBlank() }
                    val shouldUpdate = normalized != lastProgress ||
                        statusDetail != lastStatusDetail ||
                        update.progressIndeterminate != lastProgressIndeterminate
                    if (!shouldUpdate) return
                    lastProgress = normalized
                    lastStatusDetail = statusDetail
                    lastProgressIndeterminate = update.progressIndeterminate
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Running,
                        progress = normalized,
                        statusDetail = statusDetail,
                        progressIndeterminate = update.progressIndeterminate,
                    )
                }
                fun updateTaskMetadata(nextTitle: String, nextFileName: String) {
                    outputFileName = nextFileName
                    downloadRepository.updateExtraTaskMetadata(task.id, nextTitle, nextFileName)
                }
                if (!downloadRepository.updateExtraTask(task.id, DownloadStatus.Running, progress = 0)) {
                    return@extraTask
                }
                val bytes = bytesProvider(task, ::updateRunningProgress, ::updateTaskMetadata)
                if (bytes == null) {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Failed,
                        progress = 0,
                        errorMessage = errorMessage,
                    )
                    _state.update { it.copy(error = errorMessage) }
                    return@extraTask
                }
                if (!downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Running,
                        progress = 99,
                        statusDetail = strings.get(R.string.download_detail_saving_file),
                    )
                ) {
                    return@extraTask
                }
                val uri = exportRepository.saveBytes(outputFileName, mimeType, bytes, relativePath)
                val byteCount = bytes.size.toLong()
                if (uri != null) {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Success,
                        progress = 100,
                        downloadedBytes = byteCount,
                        totalBytes = byteCount,
                        localUri = uri.toString(),
                    )
                } else {
                    downloadRepository.updateExtraTask(
                        task.id,
                        DownloadStatus.Failed,
                        progress = 0,
                        errorMessage = errorMessage,
                    )
                    _state.update { it.copy(error = errorMessage) }
                }
            } catch (err: CancellationException) {
                downloadRepository.updateExtraTask(
                    task.id,
                    DownloadStatus.Cancelled,
                    progress = 0,
                    errorMessage = err.message,
                )
                throw err
            } catch (err: Throwable) {
                val message = extraTaskErrorMessage(errorMessage, err)
                AppLog.w(TAG, "[extra-task] bytes failed, type=$type, file=$fileName", err)
                downloadRepository.updateExtraTask(
                    task.id,
                    DownloadStatus.Failed,
                    progress = 0,
                    errorMessage = message,
                )
                _state.update { it.copy(error = message) }
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun prefixedName(prefix: String, base: String): String {
        val safePrefix = prefix.trim()
        val safeBase = base.trim()
        return if (safePrefix.isBlank()) safeBase else "$safePrefix - $safeBase"
    }

    private fun extensionFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#")
        val ext = clean.substringAfterLast('.', "")
        return if (ext.isBlank() || ext.length > 4) "jpg" else ext
    }

    private fun pickSubtitle(list: List<SubtitleInfo>, current: String?): String? {
        if (list.isEmpty()) return null
        if (current != null && list.any { it.lan == current }) return current
        val zh = list.firstOrNull { it.lan.startsWith("zh") || it.name.contains("涓枃") }
        return zh?.lan ?: list.first().lan
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
            is IllegalArgumentException -> strings.get(R.string.parse_error_invalid_input)
            is BiliHttpException -> strings.get(R.string.parse_error_failed)
            else -> err.message ?: strings.get(R.string.common_error_unknown)
        }
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









