package com.happycola233.bilitools.ui.parse

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.viewModelScope
import com.happycola233.bilitools.core.AppContainer
import com.happycola233.bilitools.core.AudioQualities
import com.happycola233.bilitools.data.DefaultDownloadQualitySettings
import com.happycola233.bilitools.data.DefaultDownloadVideoCodec
import com.happycola233.bilitools.data.DownloadPreferenceGroup
import com.happycola233.bilitools.data.DownloadPreferenceMemorySettings
import com.happycola233.bilitools.data.DownloadQualityMode
import com.happycola233.bilitools.data.RememberedDownloadPreferences
import com.happycola233.bilitools.data.SettingsRepository
import com.happycola233.bilitools.data.model.AudioStream
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.data.model.VideoCodec
import com.happycola233.bilitools.data.model.VideoStream
import java.time.LocalDate
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadPreferenceMemoryTest {
    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: ParseViewModel

    private val defaults = DefaultDownloadQualitySettings(
        resolutionMode = DownloadQualityMode.Fixed,
        fixedResolutionId = 120,
        codec = DefaultDownloadVideoCodec.Av1,
        audioBitrateMode = DownloadQualityMode.Fixed,
        fixedAudioBitrateId = AudioQualities.LOSSLESS_FLAC,
    )

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val container = AppContainer(context)
        repository = container.settingsRepository
        repository.setDefaultDownloadQuality(defaults)
        repository.setDownloadPreferenceMemory(
            DownloadPreferenceMemorySettings(groups = DownloadPreferenceGroup.entries.toSet()),
        )
        viewModel = ParseViewModel(
            container.mediaRepository,
            container.opusRepository,
            container.extrasRepository,
            container.downloadRepository,
            repository,
            container.authRepository,
            container.strings,
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun changingCodecOnLimitedResourceDoesNotRememberAutomaticResolutionOrAudioDowngrade() {
        enterResource(listOf(video(80, VideoCodec.Avc), video(80, VideoCodec.Hevc)))
        assertEquals(80, viewModel.state.value.selectedResolutionId)
        assertEquals(VideoCodec.Avc, viewModel.state.value.selectedCodec)
        assertEquals(30280, viewModel.state.value.selectedAudioId)

        viewModel.setCodec(VideoCodec.Hevc)

        assertEquals(defaults, repository.currentDefaultDownloadQuality())
        assertNull(repository.rememberedDownloadPreferences().quality.fixedResolutionId)
        assertNull(repository.rememberedDownloadPreferences().quality.fixedAudioBitrateId)
        enterResource(listOf(video(120, VideoCodec.Hevc)), losslessAudio())
        assertEquals(120, viewModel.state.value.selectedResolutionId)
        assertEquals(VideoCodec.Hevc, viewModel.state.value.selectedCodec)
        assertEquals(AudioQualities.LOSSLESS_FLAC, viewModel.state.value.selectedAudioId)
    }

    @Test
    fun choosingResolutionDoesNotRememberCodecFallbackAndSurvivesRepositoryReload() {
        enterResource(listOf(video(80, VideoCodec.Avc), video(64, VideoCodec.Avc)))
        viewModel.setResolution(64)
        viewModel.setAudioBitrate(30280)

        val remembered = repository.rememberedDownloadPreferences()
        assertNull(remembered.quality.codec)
        assertEquals(64, remembered.quality.fixedResolutionId)
        assertEquals(30280, remembered.quality.fixedAudioBitrateId)
        assertEquals(remembered, SettingsRepository(RuntimeEnvironment.getApplication()).rememberedDownloadPreferences())

        enterResource(listOf(video(64, VideoCodec.Av1), video(120, VideoCodec.Av1)), losslessAudio())
        assertEquals(64, viewModel.state.value.selectedResolutionId)
        assertEquals(VideoCodec.Av1, viewModel.state.value.selectedCodec)
        assertEquals(30280, viewModel.state.value.selectedAudioId)
        assertEquals(defaults, repository.currentDefaultDownloadQuality())
    }

    @Test
    fun disablingQualityGroupsUsesDefaultsAndDoesNotOverwriteSavedChoices() {
        viewModel.setResolution(64)
        viewModel.setCodec(VideoCodec.Hevc)
        viewModel.setAudioBitrate(30280)
        val saved = repository.rememberedDownloadPreferences()
        repository.setDownloadPreferenceMemory(
            DownloadPreferenceMemorySettings(groups = setOf(DownloadPreferenceGroup.Images)),
        )

        viewModel.clear()
        assertDefaultQuality()
        viewModel.setResolution(80)
        viewModel.setCodec(VideoCodec.Avc)
        viewModel.setAudioBitrateMode(QualityMode.Lowest)
        assertEquals(saved, repository.rememberedDownloadPreferences())

        repository.setDownloadPreferenceMemory(
            DownloadPreferenceMemorySettings(groups = DownloadPreferenceGroup.entries.toSet()),
        )
        viewModel.clear()
        assertEquals(64, viewModel.state.value.selectedResolutionId)
        assertEquals(VideoCodec.Hevc, viewModel.state.value.selectedCodec)
        assertEquals(30280, viewModel.state.value.selectedAudioId)
    }

    @Test
    fun masterSwitchIgnoresSavedChoicesAndDisablesWritesForEveryGroup() {
        repository.rememberDownloadPreferences(
            RememberedDownloadPreferences(
                outputType = OutputType.AudioOnly,
                streamFormat = StreamFormat.Mp4,
                embedSubtitles = true,
                embedLyrics = true,
                subtitleExport = true,
                aiSummaryExport = true,
                nfoCollection = true,
                nfoSingle = true,
                danmakuLive = true,
                danmakuHistory = true,
                imageIds = setOf("cover", "first_frame"),
            ),
        )
        val saved = repository.rememberedDownloadPreferences()
        repository.setDownloadPreferenceMemory(
            DownloadPreferenceMemorySettings(enabled = false, groups = DownloadPreferenceGroup.entries.toSet()),
        )

        enterResource(listOf(video(120, VideoCodec.Av1)), losslessAudio())
        val state = viewModel.state.value
        assertEquals(OutputType.AudioVideo, state.outputType)
        assertEquals(StreamFormat.Dash, state.format)
        assertFalse(state.embedSubtitlesEnabled)
        assertFalse(state.embedLyricsEnabled)
        assertFalse(state.subtitleEnabled)
        assertFalse(state.aiSummaryEnabled)
        assertFalse(state.nfoCollectionEnabled)
        assertFalse(state.nfoSingleEnabled)
        assertFalse(state.danmakuLiveEnabled)
        assertFalse(state.danmakuHistoryEnabled)
        assertEquals(emptySet<String>(), state.selectedImageIds)
        assertDefaultQuality()

        viewModel.setOutputType(OutputType.VideoOnly)
        viewModel.setFormat(StreamFormat.Flv)
        viewModel.setResolution(64)
        viewModel.setCodec(VideoCodec.Hevc)
        viewModel.setAudioBitrate(30280)
        viewModel.setEmbedSubtitlesEnabled(true)
        viewModel.setEmbedLyricsEnabled(true)
        viewModel.setSubtitleEnabled(true)
        viewModel.setAiSummaryEnabled(true)
        viewModel.setNfoCollectionEnabled(true)
        viewModel.setNfoSingleEnabled(true)
        viewModel.setDanmakuLiveEnabled(true)
        viewModel.setDanmakuHistoryEnabled(true)
        viewModel.setImageSelection("cover", true)
        viewModel.setOpusContentEnabled(false)
        viewModel.setOpusImagesEnabled(false)
        assertEquals(saved, repository.rememberedDownloadPreferences())
        assertEquals(defaults, repository.currentDefaultDownloadQuality())
    }

    @Test
    fun changingAvailableImageDoesNotForgetRememberedImageMissingFromCurrentResource() {
        repository.rememberDownloadPreferences(
            RememberedDownloadPreferences(imageIds = setOf("cover", "first_frame")),
        )
        enterResource(listOf(video(80, VideoCodec.Avc)))
        // 模拟这次资源只有封面：refreshExtras 会将不提供的图片从当前界面移除。
        updateState(viewModel.state.value.copy(selectedImageIds = setOf("cover")))
        viewModel.setImageSelection("cover", false)

        assertEquals(setOf("first_frame"), repository.rememberedDownloadPreferences().imageIds)
        enterResource(listOf(video(120, VideoCodec.Av1)))
        assertEquals(setOf("first_frame"), viewModel.state.value.selectedImageIds)
    }

    @Test
    fun newParseResetsHistoryTimeAndRestoresIndependentExportChoices() {
        repository.rememberDownloadPreferences(
            RememberedDownloadPreferences(
                danmakuHistory = true,
                subtitleExport = true,
                embedSubtitles = false,
            ),
        )
        viewModel.setDanmakuDate("2020-01-02")
        viewModel.setDanmakuHour("03")
        enterResource(listOf(video(80, VideoCodec.Avc)))

        assertEquals(LocalDate.now().toString(), viewModel.state.value.danmakuDate)
        assertEquals("", viewModel.state.value.danmakuHour)
        assertTrue(viewModel.state.value.subtitleEnabled)
        assertFalse(viewModel.state.value.embedSubtitlesEnabled)
    }

    @Test
    fun invalidNewInputKeepsLanguageChoicesForRetainedParseResult() {
        enterResource(listOf(video(80, VideoCodec.Avc)))
        val choices = SubtitleLanguageSelection.Languages(setOf("en"))
        val info = MediaInfo(MediaType.Video, "previous", MediaNfo(), viewModel.state.value.items)
        updateState(viewModel.state.value.copy(
            mediaInfo = info,
            outputType = null,
            subtitleEnabled = true,
            embedSubtitlesEnabled = true,
            embedLyricsEnabled = true,
            subtitleLanguageSelection = choices,
            embedSubtitleSelection = choices,
            embedLyricsLanguage = "en",
        ))

        viewModel.parse("this is not a bilibili link")
        shadowOf(Looper.getMainLooper()).idle()

        val retained = viewModel.state.value
        assertEquals(info, retained.mediaInfo)
        assertEquals(choices, retained.subtitleLanguageSelection)
        assertEquals(choices, retained.embedSubtitleSelection)
        assertEquals("en", retained.embedLyricsLanguage)
        assertEquals(true, retained.inputError != null)
    }

    @Test
    fun pageSectionAndCollectionStreamRefreshKeepsQualityUntilNewCapabilitiesArrive() {
        enterResource(listOf(video(80, VideoCodec.Hevc), video(120, VideoCodec.Hevc)), losslessAudio())
        viewModel.setResolution(80)
        viewModel.setCodec(VideoCodec.Hevc)
        viewModel.setAudioBitrate(30280)

        // 切页、分区与合集共用这一步；候选项清空不能被当作“用户选择的编码不可用”。
        val loading = normalizeQuality(viewModel.state.value.withoutLoadedStreams())
        assertEquals(80, loading.selectedResolutionId)
        assertEquals(VideoCodec.Hevc, loading.selectedCodec)
        assertEquals(30280, loading.selectedAudioId)
        assertEquals(emptyList<VideoStream>(), loading.videoStreams)

        val nextPage = normalizeQuality(loading.copy(
            videoStreams = listOf(video(80, VideoCodec.Avc), video(80, VideoCodec.Hevc), video(120, VideoCodec.Hevc)),
            audioStreams = losslessAudio(),
        ))
        assertEquals(80, nextPage.selectedResolutionId)
        assertEquals(VideoCodec.Hevc, nextPage.selectedCodec)
        assertEquals(30280, nextPage.selectedAudioId)

        val limitedPage = normalizeQuality(loading.copy(
            videoStreams = listOf(video(64, VideoCodec.Avc)),
            audioStreams = listOf(AudioStream(30216, url = "")),
        ))
        assertEquals(64, limitedPage.selectedResolutionId)
        assertEquals(VideoCodec.Avc, limitedPage.selectedCodec)
        assertEquals(30216, limitedPage.selectedAudioId)
        assertEquals(80, repository.rememberedDownloadPreferences().quality.fixedResolutionId)
        assertEquals(DefaultDownloadVideoCodec.Hevc, repository.rememberedDownloadPreferences().quality.codec)
    }

    private fun assertDefaultQuality() {
        assertEquals(QualityMode.Fixed, viewModel.state.value.resolutionMode)
        assertEquals(120, viewModel.state.value.selectedResolutionId)
        assertEquals(VideoCodec.Av1, viewModel.state.value.selectedCodec)
        assertEquals(QualityMode.Fixed, viewModel.state.value.audioBitrateMode)
        assertEquals(AudioQualities.LOSSLESS_FLAC, viewModel.state.value.selectedAudioId)
    }

    private fun normalizeQuality(state: ParseUiState): ParseUiState = ParseViewModel::class.java
        .getDeclaredMethod("normalizeQualityModes", ParseUiState::class.java)
        .apply { isAccessible = true }.invoke(viewModel, state) as ParseUiState

    /**
     * 复用解析成功后的初始化与资源限制处理，隔离网络；再通过真实公开 setter 操作并检查
     * 持久化结果，覆盖前一个资源受限、后一个资源恢复能力的跨资源场景。
     */
    private fun enterResource(
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream> = listOf(AudioStream(30280, url = "")),
    ) {
        val item = MediaItem(
            title = "偏好回归测试",
            coverUrl = "",
            description = "",
            url = "",
            duration = 60,
            pubTime = 0,
            type = MediaType.Video,
            isTarget = true,
            index = 0,
        )
        var next = viewModel.state.value.copy(
            items = listOf(item),
            selectedItemIndex = 0,
            selectedItemIndices = listOf(0),
            videoStreams = videoStreams,
            audioStreams = audioStreams,
        )
        next = ParseViewModel::class.java.getDeclaredMethod(
            "applyInitialDownloadOptions", ParseUiState::class.java, MediaType::class.java,
        ).apply { isAccessible = true }.invoke(viewModel, next, MediaType.Video) as ParseUiState
        listOf("applyDefaultDownloadQuality", "normalizeQualityModes").forEach { name ->
            next = ParseViewModel::class.java.getDeclaredMethod(name, ParseUiState::class.java)
                .apply { isAccessible = true }.invoke(viewModel, next) as ParseUiState
        }
        updateState(next)
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateState(state: ParseUiState) {
        val flow = ParseViewModel::class.java.getDeclaredField("_state")
            .apply { isAccessible = true }.get(viewModel) as MutableStateFlow<ParseUiState>
        flow.value = state
    }

    private fun video(id: Int, codec: VideoCodec) = VideoStream(id, StreamFormat.Dash, codec = codec, url = "")

    private fun losslessAudio() = listOf(
        AudioStream(AudioQualities.LOSSLESS_FLAC, url = ""),
        AudioStream(30280, url = ""),
    )
}
