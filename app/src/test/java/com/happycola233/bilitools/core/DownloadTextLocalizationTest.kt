package com.happycola233.bilitools.core

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.icu.text.ListFormatter
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessage
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.VideoCodec
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "en-rUS")
class DownloadTextLocalizationTest {
    @Test
    fun historicalChineseStatusAndQualityLabelsAreRenderedInTheCurrentLanguage() {
        val chinese = languageContext("zh-Hans")
        val english = languageContext("en")
        val legacy = task().copy(
            statusDetail = chinese.getString(R.string.download_detail_converting_audio),
            errorMessage = chinese.getString(R.string.download_failure_save),
            embedWarning = chinese.getString(R.string.download_metadata_cover_failed),
            mediaParams = DownloadMediaParams(
                resolution = chinese.getString(R.string.parse_resolution_dolby),
                codec = chinese.getString(R.string.parse_codec_hevc),
                audioBitrate = chinese.getString(R.string.parse_bitrate_hires),
            ),
        )
        val migrated = DownloadMessageCatalog(english).capture(legacy)
        assertEquals(legacy.statusDetail, migrated.statusDetail)
        assertEquals(english.getString(R.string.download_detail_converting_audio), migrated.localizedStatusDetail(english))
        assertEquals(english.getString(R.string.download_failure_save), migrated.localizedErrorMessage(english))
        assertEquals(english.getString(R.string.download_metadata_cover_failed), migrated.localizedEmbedWarning(english))
        assertEquals(126, migrated.mediaParams!!.resolutionId)
        assertEquals(VideoCodec.Hevc, migrated.mediaParams.codecType)
        assertEquals(AudioQualities.HI_RES_LOSSLESS, migrated.mediaParams.audioQualityId)
        val labels = migrated.mediaParams.localized(english)
        assertEquals(english.getString(R.string.parse_resolution_dolby), labels.resolution)
        assertEquals(english.getString(R.string.parse_bitrate_hires), labels.audioBitrate)
    }

    @Test
    fun savedMessagesContainStableCodesAndRemainTranslatableAfterReload() {
        val message = DownloadMessage(DownloadMessageCode.DetailFetchingDanmakuSegment, countArguments = listOf(2, 7))
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DownloadItem::class.java)
        val json = adapter.toJson(task().copy(statusDetail = message.resolve(languageContext("zh-Hans")), statusMessage = message))
        assertTrue(json.contains("DetailFetchingDanmakuSegment"))
        assertFalse(json.contains("resourceId"))
        val restored = adapter.fromJson(json)!!
        listOf("en", "ja", "ar").forEach { tag ->
            val context = languageContext(tag)
            assertEquals(context.getString(R.string.download_detail_fetching_danmaku_segment, 2, 7), restored.localizedStatusDetail(context))
        }
    }

    @Test
    fun clearingOrReplacingRawMessagesDoesNotLeaveStaleTranslations() {
        val english = languageContext("en")
        val catalog = DownloadMessageCatalog(english)
        val previous = catalog.capture(task().copy(
            errorMessage = english.getString(R.string.download_failure_save),
            statusDetail = english.getString(R.string.download_detail_saving_file),
            embedWarning = english.getString(R.string.download_metadata_cover_failed),
        ))
        assertNotNull(previous.failureMessage)
        val cleared = catalog.capture(previous.copy(errorMessage = null, statusDetail = null, embedWarning = null), previous)
        assertNull(cleared.failureMessage)
        assertNull(cleared.statusMessage)
        assertTrue(cleared.embeddingMessages.isEmpty())
        val externalError = "HTTP 403: external server response"
        val replaced = catalog.capture(previous.copy(errorMessage = externalError), previous)
        assertNull(replaced.failureMessage)
        assertEquals(externalError, replaced.localizedErrorMessage(languageContext("ar")))
    }

    @Test
    fun restoredInterruptedTaskUsesTheNewFailureInsteadOfThePreviousFailure() {
        val context = languageContext("en")
        val previous = DownloadMessageCatalog(context).capture(task().copy(errorMessage = context.getString(R.string.download_failure_save)))
        val restored = DownloadMessageCatalog(context).capture(previous.copy(errorMessage = context.getString(R.string.download_error_unsafe_exit)))
        assertEquals(DownloadMessageCode.ErrorUnsafeExit, restored.failureMessage!!.code)
    }

    @Test
    fun anErrorCreatedBeforeALanguageSwitchKeepsItsMessageIdentity() {
        val previousLanguage = languageContext("es")
        val currentLanguage = languageContext("ja")
        val captured = DownloadMessageCatalog(currentLanguage).capture(
            task().copy(errorMessage = previousLanguage.getString(R.string.download_failure_convert_audio)),
        )
        assertEquals(DownloadMessageCode.FailureConvertAudio, captured.failureMessage!!.code)
        assertEquals(currentLanguage.getString(R.string.download_failure_convert_audio), captured.localizedErrorMessage(currentLanguage))
    }

    @Test
    fun oldJsonWithoutMessageFieldsAndUnrecognizedErrorsStillLoads() {
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DownloadItem::class.java)
        val oldJson = """{"id":1,"groupId":1,"taskType":"Video","title":"title","fileName":"video.mp4","url":"","status":"Failed","progress":0,"errorMessage":"Unrecognized external detail"}"""
        val oldItem = adapter.fromJson(oldJson)!!
        assertNull(oldItem.failureMessage)
        assertEquals(oldItem.errorMessage, DownloadMessageCatalog(languageContext("en")).capture(oldItem).localizedErrorMessage(languageContext("ar")))
    }

    @Test
    fun incompleteSubtitleListsUseTheCurrentLanguagesListFormatting() {
        val titles = listOf("English", "日本語", "العربية")
        val message = DownloadMessage(DownloadMessageCode.EmbedMissingLanguages, textArguments = titles)
        listOf("en", "ar", "zh-Hans").forEach { tag ->
            val context = languageContext(tag)
            val list = ListFormatter.getInstance(context.resources.configuration.locales[0]).format(titles)
            assertEquals(context.getString(R.string.download_embed_missing_languages, list), message.resolve(context))
        }
    }

    private fun task() = DownloadItem(
        id = 1,
        groupId = 1,
        taskType = DownloadTaskType.Video,
        title = "title",
        fileName = "video.mp4",
        url = "",
        status = DownloadStatus.Running,
        progress = 20,
    )

    private fun languageContext(tag: String): Context {
        val application = RuntimeEnvironment.getApplication()
        return application.createConfigurationContext(Configuration(application.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        })
    }
}
