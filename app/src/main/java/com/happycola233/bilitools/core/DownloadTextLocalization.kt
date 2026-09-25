package com.happycola233.bilitools.core

import android.content.Context
import android.content.res.Configuration
import android.icu.text.ListFormatter
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadMediaParams
import com.happycola233.bilitools.data.model.DownloadMessage
import com.happycola233.bilitools.data.model.DownloadMessageCode
import com.happycola233.bilitools.data.model.VideoCodec
import java.util.Locale

fun DownloadMessage.resolve(context: Context): String {
    val localized = context.localizedContext()
    val arguments: Array<out Any> = if (code == DownloadMessageCode.EmbedMissingLanguages) {
        arrayOf(ListFormatter.getInstance(localized.resources.configuration.locales[0]).format(textArguments))
    } else if (countArguments.isNotEmpty()) {
        countArguments.toTypedArray()
    } else {
        textArguments.toTypedArray()
    }
    return localized.getString(code.resourceId, *arguments)
}

fun DownloadItem.localizedStatusDetail(context: Context): String? =
    statusMessage?.resolve(context) ?: statusDetail

fun DownloadItem.localizedErrorMessage(context: Context): String? =
    failureMessage?.resolve(context) ?: errorMessage

fun DownloadItem.localizedEmbedWarning(
    context: Context,
    excludedCodes: Set<DownloadMessageCode> = emptySet(),
): String? = if (embeddingMessages.isNotEmpty()) {
    embeddingMessages.filterNot { it.code in excludedCodes }
        .joinToString(" · ") { it.resolve(context) }.takeIf(String::isNotBlank)
} else {
    embedWarning
}

fun DownloadMediaParams.localized(context: Context): DownloadMediaParams {
    val localized = context.localizedContext()
    return copy(
        resolution = resolutionId?.let { localized.getString(resolutionLabelRes(it, resolutionHeight)) } ?: resolution,
        codec = codecType?.let {
            localized.getString(when (it) {
                VideoCodec.Avc -> R.string.parse_codec_avc
                VideoCodec.Hevc -> R.string.parse_codec_hevc
                VideoCodec.Av1 -> R.string.parse_codec_av1
            })
        } ?: codec,
        audioBitrate = audioQualityId?.let { localized.getString(AudioQualities.labelRes(it)) } ?: audioBitrate,
    )
}

internal fun resolutionLabelRes(id: Int, height: Int?): Int = when (id) {
    127 -> R.string.parse_resolution_8k
    126 -> R.string.parse_resolution_dolby
    125 -> R.string.parse_resolution_hdr
    120 -> R.string.parse_resolution_4k
    116 -> R.string.parse_resolution_1080_60
    112 -> R.string.parse_resolution_1080_high
    80 -> R.string.parse_resolution_1080
    64 -> R.string.parse_resolution_720
    32 -> R.string.parse_resolution_480
    16 -> R.string.parse_resolution_360
    6 -> R.string.parse_resolution_240
    else -> when {
        (height ?: 0) >= 4320 -> R.string.parse_resolution_8k
        (height ?: 0) >= 2160 -> R.string.parse_resolution_4k
        (height ?: 0) >= 1080 -> R.string.parse_resolution_1080
        (height ?: 0) >= 720 -> R.string.parse_resolution_720
        (height ?: 0) >= 480 -> R.string.parse_resolution_480
        (height ?: 0) >= 360 -> R.string.parse_resolution_360
        else -> R.string.parse_resolution_other
    }
}

/**
 * 兼容已有下载记录及仍接收字符串的附加任务接口，在写入状态时把已知文案转为稳定消息。
 * 外部错误和用户内容保持原样；进度更新复用已有消息，不在每个下载进度回调中查找资源。
 */
internal class DownloadMessageCatalog(private val context: Context) {
    private val supportedContexts by lazy {
        AppLanguage.entries.filterNot { it == AppLanguage.System }.map { language ->
            context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.languageTag)) },
            )
        }
    }
    // 异步操作产生错误后也可能立刻切换语言，因此旧原文可来自任一受支持的语言。
    private val fixedMessages by lazy {
        buildMap {
            supportedContexts.forEach { languageContext ->
                DownloadMessageCode.entries.filterNot { it.hasArguments }.forEach { code ->
                    put(languageContext.getString(code.resourceId), DownloadMessage(code))
                }
            }
        }
    }
    private val resolutionIds = listOf(127, 126, 125, 120, 116, 112, 80, 64, 32, 16, 6, 0)

    @Synchronized
    fun capture(item: DownloadItem, previous: DownloadItem? = null): DownloadItem {
        if (previous != null &&
            item.statusDetail == previous.statusDetail && item.statusMessage == previous.statusMessage &&
            item.errorMessage == previous.errorMessage && item.failureMessage == previous.failureMessage &&
            item.embedWarning == previous.embedWarning && item.embeddingMessages == previous.embeddingMessages &&
            item.mediaParams == previous.mediaParams
        ) return item
        fun captureText(
            raw: String?,
            message: DownloadMessage?,
            oldRaw: String?,
            oldMessage: DownloadMessage?,
            legacyMessage: DownloadMessage? = null,
        ): DownloadMessage? {
            if (raw == null) return null
            // copy() 改变原文但沿用旧消息时，清除旧消息；显式传入的新消息和未改变的状态可直接保留。
            return fixedMessages[raw] ?: if (message != null && (previous == null || message != oldMessage || raw == oldRaw)) {
                message
            } else {
                legacyMessage
            }
        }
        // 旧版仅提供简体中文，这两条包含动态参数的历史文案保留其原始参数再重新渲染。
        val legacyChinese = supportedContexts.first()
        fun legacyMissingLanguages(raw: String): DownloadMessage? {
            val prefix = legacyChinese.getString(R.string.download_embed_missing_languages, "")
            return raw.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.let {
                DownloadMessage(DownloadMessageCode.EmbedMissingLanguages, textArguments = it.split('、'))
            }
        }
        val legacyFailure = item.errorMessage?.let { raw ->
            val prefix = legacyChinese.getString(R.string.download_failure_extra, item.title, "")
            raw.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.let { detail ->
                DownloadMessage(DownloadMessageCode.FailureExtra, textArguments = listOf(item.title, detail))
            }
        }
        val warningMessages = when {
            item.embedWarning == null -> emptyList()
            item.embeddingMessages.isNotEmpty() && (previous == null ||
                item.embeddingMessages != previous.embeddingMessages || item.embedWarning == previous.embedWarning) -> item.embeddingMessages
            else -> item.embedWarning.split('；', '\n').mapNotNull { fixedMessages[it] ?: legacyMissingLanguages(it) }
                .takeIf { messages -> messages.size == item.embedWarning.split('；', '\n').size }
                .orEmpty()
        }
        fun matchesLegacyLabel(label: String?, resourceId: Int): Boolean = label != null &&
            supportedContexts.any { label == it.getString(resourceId) }
        val params = item.mediaParams?.let { media ->
            media.copy(
                resolutionId = media.resolutionId ?: resolutionIds.firstOrNull {
                    matchesLegacyLabel(media.resolution, resolutionLabelRes(it, null))
                },
                audioQualityId = media.audioQualityId ?: (AudioQualities.allIds + 0).firstOrNull {
                    matchesLegacyLabel(media.audioBitrate, AudioQualities.labelRes(it))
                },
                codecType = media.codecType ?: VideoCodec.entries.firstOrNull {
                    matchesLegacyLabel(media.codec, when (it) {
                        VideoCodec.Avc -> R.string.parse_codec_avc
                        VideoCodec.Hevc -> R.string.parse_codec_hevc
                        VideoCodec.Av1 -> R.string.parse_codec_av1
                    })
                },
            )
        }
        return item.copy(
            statusMessage = captureText(item.statusDetail, item.statusMessage, previous?.statusDetail, previous?.statusMessage),
            failureMessage = captureText(item.errorMessage, item.failureMessage, previous?.errorMessage, previous?.failureMessage, legacyFailure),
            embeddingMessages = warningMessages,
            mediaParams = params,
        )
    }
}
