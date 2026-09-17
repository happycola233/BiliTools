package com.happycola233.bilitools.ui.parse

import com.happycola233.bilitools.data.displayName
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.subtitleLanguageDisplayName
import kotlinx.coroutines.CancellationException

/** 复制预览逐项保留成功、缺失与失败，不能用部分成功掩盖用户选择的其他语言。 */
internal suspend fun loadSubtitleCopyEntries(
    title: String,
    selection: SubtitleLanguageSelection,
    knownSubtitles: List<SubtitleInfo>,
    loadSubtitles: suspend () -> List<SubtitleInfo>,
    loadContent: suspend (SubtitleInfo) -> String,
    unavailableMessage: String,
    errorMessage: (Throwable) -> String,
): List<SubtitleCopyEntry> {
    if (selection.isEmpty) return emptyList()
    fun missingSubtitle(language: String, subtitles: List<SubtitleInfo>) =
        (subtitles + knownSubtitles).firstOrNull { it.lan == language }?.copy(url = "")
            ?: SubtitleInfo(language, subtitleLanguageDisplayName(language), "")

    val requestedLanguages = (selection as? SubtitleLanguageSelection.Languages)?.languages.orEmpty()
    val subtitles = try {
        loadSubtitles()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        val message = errorMessage(error)
        return if (requestedLanguages.isEmpty()) {
            listOf(SubtitleCopyEntry(title, null, null, message))
        } else requestedLanguages.map { language ->
            SubtitleCopyEntry(title, missingSubtitle(language, emptyList()).displayName, null, message)
        }
    }
    val available = selectSubtitles(subtitles, selection)
        .filter { it.url.isNotBlank() }.distinctBy { it.lan }
    val availableLanguages = available.map { it.lan }.toSet()
    val missing = requestedLanguages.filterNot(availableLanguages::contains)
        .map { missingSubtitle(it, subtitles) }
    val choices = available + missing
    if (choices.isEmpty()) return listOf(SubtitleCopyEntry(title, null, null, unavailableMessage))

    return choices.map { subtitle ->
        if (subtitle.url.isBlank()) {
            SubtitleCopyEntry(title, subtitle.displayName, null, unavailableMessage)
        } else try {
            val content = loadContent(subtitle).takeIf { it.isNotBlank() }
            SubtitleCopyEntry(title, subtitle.displayName, content, unavailableMessage.takeIf { content == null })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SubtitleCopyEntry(title, subtitle.displayName, null, errorMessage(error))
        }
    }
}
