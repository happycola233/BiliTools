package com.happycola233.bilitools.ui.parse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.BiliHttpException
import com.happycola233.bilitools.core.NfoGenerator
import com.happycola233.bilitools.core.StringProvider
import com.happycola233.bilitools.data.AuthRepository
import com.happycola233.bilitools.data.DownloadRepository
import com.happycola233.bilitools.data.ExportRepository
import com.happycola233.bilitools.data.ExtrasRepository
import com.happycola233.bilitools.data.MediaRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

enum class QualityMode {
    Highest,
    Lowest,
    Fixed,
}

data class ParseUiState(
    val loading: Boolean = false,
    val streamLoading: Boolean = false,
    val downloadStarting: Boolean = false,
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
    val resolutionMode: QualityMode = QualityMode.Fixed,
    val audioBitrateMode: QualityMode = QualityMode.Fixed,
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
    val streamInfo: String = "",
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
    private val authRepository: AuthRepository,
    private val strings: StringProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(ParseUiState())
    val state: StateFlow<ParseUiState> = _state.asStateFlow()
    private val fullResolutionIds = listOf(127, 126, 125, 120, 116, 112, 80, 64, 32, 16, 6)
    private val fullAudioIds = listOf(30280, 30232, 30216)
    private val offsetMap = mutableMapOf<Int, String>()
    private val collectionStatCache = mutableMapOf<String, MediaStat>()
    private val itemStatCache = mutableMapOf<String, MediaStat>()
    private val itemDescriptionCache = mutableMapOf<String, String>()

    init {
        refreshLoginState()
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
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
                            streamInfo = "",
                            warning = null,
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = info.list.getOrNull(defaultIndex)?.stat,
                            isLoggedIn = authRepository.isLoggedIn(),
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
                _state.update {
                    it.copy(loading = false, error = mapError(err))
                }
            }
        }
    }

    fun clear() {
        val selectedType = _state.value.selectedMediaType
        offsetMap.clear()
        collectionStatCache.clear()
        itemStatCache.clear()
        itemDescriptionCache.clear()
        _state.value = ParseUiState(selectedMediaType = selectedType)
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
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
                    playUrlInfo = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    resolutions = emptyList(),
                    codecs = emptyList(),
                    audioBitrates = emptyList(),
                    selectedResolutionId = null,
                    selectedCodec = null,
                    selectedAudioId = null,
                    streamInfo = "",
                    warning = null,
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
                    playUrlInfo = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    resolutions = emptyList(),
                    codecs = emptyList(),
                    audioBitrates = emptyList(),
                    selectedResolutionId = null,
                    selectedCodec = null,
                    selectedAudioId = null,
                    streamInfo = "",
                    warning = null,
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
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
                            streamInfo = "",
                            warning = null,
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
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
                _state.update { it.copy(loading = false, error = mapError(err)) }
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
            _state.update { it.copy(loading = true, error = null, notice = null) }
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
                            streamInfo = "",
                            warning = null,
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
            }.onFailure { err ->
                _state.update { it.copy(loading = false, error = mapError(err)) }
            }
        }
    }

    fun setCollectionMode(enabled: Boolean) {
        val info = _state.value.mediaInfo ?: return
        if (_state.value.collectionMode == enabled) return
        val target = _state.value.selectedSectionId
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notice = null) }
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
                            streamInfo = "",
                            warning = null,
                            collectionPreviewIndex = null,
                            collectionPreviewStat = null,
                            selectedItemStat = updated.list.getOrNull(defaultIndex)?.stat,
                        ),
                    )
                }
                updated.list.getOrNull(defaultIndex)?.let { item ->
                    refreshExtras(updated, item)
                }
            }.onFailure { err ->
                _state.update { it.copy(loading = false, error = mapError(err)) }
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
            )
        }
        loadStream()
    }

    fun setOutputType(type: OutputType?) {
        _state.update { it.copy(outputType = type) }
    }

    fun setResolution(id: Int) {
        _state.update { it.copy(selectedResolutionId = id) }
        updateStreamInfo()
    }

    fun setResolutionMode(mode: QualityMode) {
        _state.update { current ->
            val selectedCount = current.selectedItemIndices.size
            val nextResolutions = resolveResolutionOptions(current.videoStreams, mode, selectedCount)
            val nextResolutionId = pickResolutionId(current.selectedResolutionId, nextResolutions)
            current.copy(
                resolutionMode = mode,
                resolutions = nextResolutions,
                selectedResolutionId = nextResolutionId,
            )
        }
        updateStreamInfo()
    }

    fun setCodec(codec: VideoCodec) {
        _state.update { it.copy(selectedCodec = codec) }
        updateStreamInfo()
    }

    fun setAudioBitrate(id: Int) {
        _state.update { it.copy(selectedAudioId = id) }
        updateStreamInfo()
    }

    fun setAudioBitrateMode(mode: QualityMode) {
        _state.update { current ->
            val selectedCount = current.selectedItemIndices.size
            val nextAudioOptions = resolveAudioOptions(current.audioStreams, mode, selectedCount)
            val nextAudioId = pickAudioId(current.selectedAudioId, nextAudioOptions)
            current.copy(
                audioBitrateMode = mode,
                audioBitrates = nextAudioOptions,
                selectedAudioId = nextAudioId,
            )
        }
        updateStreamInfo()
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

    fun loadStream() {
        if (_state.value.mediaInfo == null) return
        val item = currentItem() ?: return
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
                    mediaRepository.getPlayUrlInfo(resolvedItem, resolvedItem.type, _state.value.format)
                val selectedCount = _state.value.selectedItemIndices.size
                val resolutions = resolveResolutionOptions(
                    playUrlInfo.video,
                    _state.value.resolutionMode,
                    selectedCount,
                )
                val codecs = buildCodecOptions(playUrlInfo.video)
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
                val isMultiSelect = selectedCount > 1
                val keepFixedResolution =
                    _state.value.resolutionMode == QualityMode.Fixed && isMultiSelect
                val selectedResolutionId = if (keepFixedResolution) {
                    _state.value.selectedResolutionId ?: resolutions.firstOrNull()?.id
                } else {
                    _state.value.selectedResolutionId
                        ?.takeIf { id -> resolutions.any { it.id == id } }
                        ?: resolutions.firstOrNull()?.id
                }
                val selectedCodec =
                    _state.value.selectedCodec
                        ?.takeIf { codec -> codecs.any { it.codec == codec } }
                        ?: pickDefaultCodec(codecs)
                val keepFixedAudio =
                    _state.value.audioBitrateMode == QualityMode.Fixed && isMultiSelect
                val selectedAudioId = if (keepFixedAudio) {
                    _state.value.selectedAudioId ?: audio.firstOrNull()?.id
                } else {
                    _state.value.selectedAudioId
                        ?.takeIf { id -> audio.any { it.id == id } }
                        ?: audio.firstOrNull()?.id
                }
                _state.update {
                    it.copy(
                        streamLoading = false,
                        playUrlInfo = playUrlInfo,
                        videoStreams = playUrlInfo.video,
                        audioStreams = playUrlInfo.audio,
                        resolutions = resolutions,
                        codecs = codecs,
                        audioBitrates = audio,
                        format = playUrlInfo.format,
                        outputType = safeOutputType,
                        selectedResolutionId = selectedResolutionId,
                        selectedCodec = selectedCodec,
                        selectedAudioId = selectedAudioId,
                        warning = if (playUrlInfo.format == StreamFormat.Dash) {
                            strings.get(R.string.parse_warning_dash)
                        } else {
                            null
                        },
                    )
                }
                updateStreamInfo()
            }.onFailure { err ->
                _state.update {
                    it.copy(streamLoading = false, error = mapError(err))
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
            _state.update { it.copy(downloadStarting = true, error = null, notice = null) }
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
                var lastDownload: DownloadItem? = null
                items.forEach { rawItem ->
                    val item = runCatching { mediaRepository.resolveItemForPlay(rawItem, rawItem.type) }
                        .getOrDefault(rawItem)
                    val playUrlInfo = if (snapshot.outputType != null) {
                        runCatching {
                            mediaRepository.getPlayUrlInfo(item, item.type, snapshot.format)
                        }.getOrNull()
                    } else {
                        null
                    }
                    val collectionAvailable = info.collection && !info.nfo.showTitle.isNullOrBlank()
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
                    val baseName = sanitizeFileName(baseNameSource.ifBlank { "BiliTools" })
                    val saved = mutableListOf<String>()

                    val groupId = downloadRepository.createGroup(
                        effectiveGroupTitle,
                        effectiveGroupSubtitle,
                        item.bvid,
                        item.coverUrl,
                    )
                    val groupRelativePath = downloadRepository.groupRelativePath(groupId)

                    val trackTotal = when {
                        useVideoNaming && pageCount > 0 -> pageCount
                        !info.paged && info.list.size > 1 -> info.list.size
                        else -> null
                    }
                    val embeddedMetadata = buildEmbeddedMetadata(
                        info = info,
                        item = item,
                        fallbackAlbum = if (useVideoNaming && videoTitle.isNotBlank()) {
                            videoTitle
                        } else {
                            effectiveGroupTitle
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
                            when (outputType) {
                                OutputType.AudioOnly -> {
                                    val audioName = buildAudioFileName(baseName, selectedAudio!!)
                                    val mediaParams = buildMediaParams(null, null, selectedAudio)
                                    lastDownload = downloadRepository.enqueue(
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
                                    val videoName = buildVideoFileName(baseName, selectedVideo!!, snapshot.selectedCodec)
                                    val mediaParams = buildMediaParams(selectedVideo, snapshot.selectedCodec, null)
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
                                        val outputName =
                                            buildMergedFileName(baseName, mergeVideo!!, snapshot.selectedCodec)
                                        val mediaParams = buildMediaParams(mergeVideo, snapshot.selectedCodec, selectedAudio)
                                        lastDownload = downloadRepository.enqueueDashMerge(
                                            groupId,
                                            downloadTitle,
                                            outputName,
                                            mergeVideo.url,
                                            selectedAudio.url,
                                            mediaParams,
                                            embeddedMetadata = embeddedMetadata,
                                        )
                                    } else {
                                        val videoName =
                                            buildVideoFileName(baseName, mergeVideo!!, snapshot.selectedCodec)
                                        val mediaParams = buildMediaParams(mergeVideo, snapshot.selectedCodec, selectedAudio)
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

                    if (snapshot.subtitleEnabled) {
                        val aid = item.aid
                        val cid = item.cid
                        val subtitleTitle = strings.get(R.string.parse_subtitle_label)
                        val subtitles = if (aid != null && cid != null) {
                            runCatching { extrasRepository.getSubtitles(aid, cid) }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        val subtitle = subtitles.firstOrNull { it.lan == snapshot.selectedSubtitleLan }
                            ?: if (snapshot.selectedSubtitleLan == null) subtitles.firstOrNull() else null
                        if (subtitle != null) {
                            val prefix = prefixedName(subtitleTitle, baseName)
                            val name = sanitizeFileName("$prefix.${subtitle.lan}.srt")
                            saveBytesTask(
                                groupId,
                                DownloadTaskType.Subtitle,
                                "${subtitleTitle} - ${subtitle.name}",
                                name,
                                null,
                                groupRelativePath,
                                { extrasRepository.getSubtitleSrt(subtitle) },
                                strings.get(R.string.parse_error_no_subtitle),
                                saved,
                            )
                        } else {
                            val message = strings.get(R.string.parse_error_no_subtitle)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.Subtitle,
                                subtitleTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        }
                    }
                    if (snapshot.aiSummaryEnabled) {
                        val aid = item.aid
                        val cid = item.cid
                        val bvid = item.bvid
                        val taskTitle = strings.get(R.string.parse_ai_summary_label)
                        if (aid != null && cid != null && bvid != null) {
                            val summaryTitle = info.nfo.showTitle?.ifBlank { item.title } ?: item.title
                            val prefix = prefixedName(taskTitle, baseName)
                            val name = sanitizeFileName("$prefix.md")
                            saveTextTask(
                                groupId,
                                DownloadTaskType.AiSummary,
                                taskTitle,
                                name,
                                null,
                                groupRelativePath,
                                { extrasRepository.getAiSummaryMarkdown(summaryTitle, bvid, aid, cid) },
                                strings.get(R.string.parse_error_no_ai),
                                saved,
                            )
                        } else {
                            val message = strings.get(R.string.parse_error_no_ai)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.AiSummary,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        }
                    }
                    if (snapshot.nfoCollectionEnabled) {
                        val taskTitle = strings.get(R.string.parse_nfo_collection)
                        if (collectionAvailable) {
                            val name = "tvshow.nfo"
                            saveTextTask(
                                groupId,
                                DownloadTaskType.NfoCollection,
                                taskTitle,
                                name,
                                null,
                                groupRelativePath,
                                { NfoGenerator.buildCollectionNfo(info) },
                                strings.get(R.string.parse_error_no_nfo),
                                saved,
                            )
                        } else {
                            val message = strings.get(R.string.parse_error_no_nfo)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.NfoCollection,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        }
                    }
                    if (snapshot.nfoSingleEnabled) {
                        val taskTitle = strings.get(R.string.parse_nfo_single)
                        val prefix = prefixedName(taskTitle, baseName)
                        val name = sanitizeFileName("$prefix.nfo")
                        saveTextTask(
                            groupId,
                            DownloadTaskType.NfoSingle,
                            taskTitle,
                            name,
                            null,
                            groupRelativePath,
                            { NfoGenerator.buildSingleNfo(info, item) },
                            strings.get(R.string.parse_error_no_nfo),
                            saved,
                        )
                    }
                    if (snapshot.danmakuLiveEnabled) {
                        val aid = item.aid
                        val cid = item.cid
                        val duration = item.duration
                        val taskTitle = strings.get(R.string.parse_danmaku_live)
                        val prefix = prefixedName(taskTitle, baseName)
                        val name = sanitizeFileName("$prefix.ass")
                        if (aid != null && cid != null) {
                            saveBytesTask(
                                groupId,
                                DownloadTaskType.DanmakuLive,
                                taskTitle,
                                name,
                                null,
                                groupRelativePath,
                                { extrasRepository.getDanmakuLiveAss(aid, cid, duration) },
                                strings.get(R.string.common_error_unknown),
                                saved,
                            )
                        } else {
                            val message = strings.get(R.string.parse_error_no_danmaku)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.DanmakuLive,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        }
                    }

                    if (snapshot.danmakuHistoryEnabled) {
                        val date = snapshot.danmakuDate
                        val hour = parseHour(snapshot.danmakuHour)
                        val taskTitle = strings.get(R.string.parse_danmaku_history)
                        val cid = item.cid
                        if (cid == null) {
                            val message = strings.get(R.string.parse_error_no_danmaku)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.DanmakuHistory,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        } else if (!isValidDate(date)) {
                            val message = strings.get(R.string.parse_error_invalid_date)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.DanmakuHistory,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        } else if (snapshot.danmakuHour.isNotBlank() && hour == null) {
                            val message = strings.get(R.string.parse_error_invalid_hour)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.DanmakuHistory,
                                taskTitle,
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        } else {
                            val prefix = prefixedName(taskTitle, baseName)
                            val name = sanitizeFileName("$prefix.ass")
                            saveBytesTask(
                                groupId,
                                DownloadTaskType.DanmakuHistory,
                                taskTitle,
                                name,
                                null,
                                groupRelativePath,
                                { extrasRepository.getDanmakuHistoryAss(cid, date, hour) },
                                strings.get(R.string.common_error_unknown),
                                saved,
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
                                val prefix = prefixedName(fileLabel, baseName)
                                val name = sanitizeFileName(
                                    "$prefix.${extensionFromUrl(thumb.url)}",
                                )
                                saveBytesTask(
                                    groupId,
                                    taskType,
                                    label,
                                    name,
                                    "image/*",
                                    groupRelativePath,
                                    { extrasRepository.fetchBytes(thumb.url) },
                                    strings.get(R.string.common_error_unknown),
                                    saved,
                                )
                            }
                        } else {
                            val message = strings.get(R.string.parse_error_no_cover)
                            downloadRepository.addExtraTask(
                                groupId,
                                DownloadTaskType.CollectionCover,
                                strings.get(R.string.parse_image_label),
                                "",
                                DownloadStatus.Failed,
                                errorMessage = message,
                            )
                            _state.update { it.copy(error = message) }
                        }
                    }
                }
                _state.update { it.copy(lastDownload = lastDownload) }
            }
        }
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
        val collectionAvailable = info.collection && !info.nfo.showTitle.isNullOrBlank()
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
        val needStat = cachedStat == null || !hasAnyStat(cachedStat)
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
        if (info.type != MediaType.Favorite && info.type != MediaType.WatchLater) return false
        return item.bvid?.isNotBlank() == true || item.aid != null
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

    private fun updateStreamInfo() {
        val state = _state.value
        val selectedVideo = selectVideoStream(state)
        val selectedAudio = selectAudioStream(state)
        val videoLabel = selectedVideo?.let { stream ->
            val resolution = mapResolutionLabel(stream)
            val codec = stream.codec?.let { codecLabel(it) }
                ?: strings.get(R.string.parse_codec_avc)
            listOfNotNull(resolution, codec).joinToString(" / ")
        }
        val audioLabel = selectedAudio?.let { audio ->
            mapAudioLabel(audio.id)
        }
        val info = buildString {
            if (!videoLabel.isNullOrBlank()) {
                append(videoLabel)
            }
            if (!audioLabel.isNullOrBlank()) {
                if (isNotEmpty()) append(" / ")
                append(audioLabel)
            }
        }
        _state.update { it.copy(streamInfo = info) }
    }

    private fun normalizeQualityModes(state: ParseUiState): ParseUiState {
        val selectedCount = state.selectedItemIndices.size
        val multiSelect = selectedCount > 1
        val nextResolutionMode = if (multiSelect) state.resolutionMode else QualityMode.Fixed
        val nextAudioMode = if (multiSelect) state.audioBitrateMode else QualityMode.Fixed
        val nextResolutions = resolveResolutionOptions(state.videoStreams, nextResolutionMode, selectedCount)
        val nextAudioOptions = resolveAudioOptions(state.audioStreams, nextAudioMode, selectedCount)
        val nextResolutionId = pickResolutionId(state.selectedResolutionId, nextResolutions)
        val nextAudioId = pickAudioId(state.selectedAudioId, nextAudioOptions)
        return state.copy(
            resolutionMode = nextResolutionMode,
            audioBitrateMode = nextAudioMode,
            resolutions = nextResolutions,
            audioBitrates = nextAudioOptions,
            selectedResolutionId = nextResolutionId,
            selectedAudioId = nextAudioId,
        )
    }

    private fun currentItem(state: ParseUiState = _state.value): MediaItem? {
        return state.items.getOrNull(state.selectedItemIndex)
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
            QualityMode.Highest -> ids.maxOrNull()
            QualityMode.Lowest -> ids.minOrNull()
            QualityMode.Fixed -> selectedId?.takeIf { ids.contains(it) } ?: ids.maxOrNull()
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
    ): Int? {
        if (options.isEmpty()) return currentId
        return currentId?.takeIf { id -> options.any { it.id == id } } ?: options.first().id
    }

    private fun pickAudioId(
        currentId: Int?,
        options: List<AudioOption>,
    ): Int? {
        if (options.isEmpty()) return currentId
        return currentId?.takeIf { id -> options.any { it.id == id } } ?: options.first().id
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

    private fun buildAudioOptions(streams: List<AudioStream>): List<AudioOption> {
        return streams.map { audio ->
            AudioOption(audio.id, mapAudioLabel(audio.id))
        }.distinctBy { it.id }.sortedByDescending { it.id }
    }

    private fun buildAudioOptionsAll(streams: List<AudioStream>): List<AudioOption> {
        val known = fullAudioIds.map { id ->
            AudioOption(id, mapAudioLabel(id))
        }
        return (known + buildAudioOptions(streams))
            .distinctBy { it.id }
            .sortedByDescending { it.id }
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
        return when (id) {
            30280 -> strings.get(R.string.parse_bitrate_192)
            30232 -> strings.get(R.string.parse_bitrate_132)
            30216 -> strings.get(R.string.parse_bitrate_64)
            else -> strings.get(R.string.parse_bitrate_other)
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
        return options.firstOrNull { it.codec == VideoCodec.Avc }?.codec
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

    private suspend fun saveTextTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        mimeType: String?,
        relativePath: String,
        contentProvider: suspend () -> String?,
        errorMessage: String,
        saved: MutableList<String>,
    ) {
        val content = runCatching { contentProvider() }.getOrNull()
        val uri = if (!content.isNullOrBlank()) {
            runCatching {
                exportRepository.saveText(fileName, mimeType, content, relativePath)
            }.getOrNull()
        } else {
            null
        }
        if (uri != null) {
            downloadRepository.addExtraTask(
                groupId,
                type,
                taskTitle,
                fileName,
                DownloadStatus.Success,
                localUri = uri.toString(),
            )
            saved.add(fileName)
        } else {
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
    }

    private suspend fun saveBytesTask(
        groupId: Long,
        type: DownloadTaskType,
        taskTitle: String,
        fileName: String,
        mimeType: String?,
        relativePath: String,
        bytesProvider: suspend () -> ByteArray?,
        errorMessage: String,
        saved: MutableList<String>,
    ) {
        val bytes = runCatching { bytesProvider() }.getOrNull()
        val uri = if (bytes != null) {
            runCatching {
                exportRepository.saveBytes(fileName, mimeType, bytes, relativePath)
            }.getOrNull()
        } else {
            null
        }
        if (uri != null) {
            downloadRepository.addExtraTask(
                groupId,
                type,
                taskTitle,
                fileName,
                DownloadStatus.Success,
                localUri = uri.toString(),
            )
            saved.add(fileName)
        } else {
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
}









