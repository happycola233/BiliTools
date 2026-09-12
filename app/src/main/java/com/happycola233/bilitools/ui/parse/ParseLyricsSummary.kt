package com.happycola233.bilitools.ui.parse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadMetadataSettings
import com.happycola233.bilitools.data.SubtitleLyricsMode
import com.happycola233.bilitools.data.isGenerated
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.OutputType
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.data.selectLyricsSubtitle
import com.happycola233.bilitools.ui.LyricsLanguageHelpButton
import java.util.Locale

/** 只展示当前结果；字幕选择直接复用下载端规则，完整说明收在问号入口内。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ParseLyricsSummary(
    state: ParseUiState,
    settings: DownloadMetadataSettings?,
    modifier: Modifier = Modifier,
) {
    val types = state.selectedItemIndices.map { state.items[it].type }.toSet()
    val hasMusic = MediaType.Music in types
    val hasVideo = types.any { it == MediaType.Video || it == MediaType.Bangumi || it == MediaType.Lesson }
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = state.outputType == OutputType.AudioOnly && (hasMusic || hasVideo),
        modifier = modifier,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = motionScheme.defaultEffectsSpec(),
        ) + fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = motionScheme.defaultEffectsSpec(),
        ) + fadeOut(animationSpec = motionScheme.defaultEffectsSpec()),
    ) {
        val summary = when {
            settings == null || !settings.embedLyrics -> stringResource(R.string.parse_lyrics_off)
            !hasVideo || (hasMusic && settings.subtitleLyrics == SubtitleLyricsMode.Off) ->
                stringResource(R.string.parse_lyrics_original)
            settings.subtitleLyrics == SubtitleLyricsMode.Off -> stringResource(R.string.parse_lyrics_off)
            // 当前字幕列表属于单个条目，不能把它当作整个批次的语言预览。
            state.isMultiSelect -> stringResource(R.string.parse_lyrics_enabled)
            else -> subtitleLyricsSummary(state, settings.subtitleLyrics)
        }
        // 留白随内容一起进出，外层不再为不可见的提示保留固定行间距。
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_lyrics_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            AnimatedContent(
                targetState = summary,
                transitionSpec = {
                    fadeIn(animationSpec = motionScheme.fastEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                },
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f),
                label = "lyricsSummary",
            ) { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LyricsLanguageHelpButton()
        }
    }
}

@Composable
private fun subtitleLyricsSummary(state: ParseUiState, mode: SubtitleLyricsMode): String {
    when (state.subtitleLoadStatus) {
        SubtitleLoadStatus.Loading -> return stringResource(R.string.parse_lyrics_checking)
        SubtitleLoadStatus.Failed -> return stringResource(R.string.parse_lyrics_check_failed)
        SubtitleLoadStatus.Ready -> Unit
    }
    val language = state.preferredLyricsSubtitleLanguage
    val subtitle = selectLyricsSubtitle(state.subtitleList, mode, language)
    return when {
        subtitle != null -> stringResource(
            if (subtitle.isGenerated) R.string.parse_lyrics_ai else R.string.parse_lyrics_manual,
            subtitleLanguageName(subtitle),
        )
        language != null && mode == SubtitleLyricsMode.ManualOnly &&
            state.subtitleList.any { it.lan == language && it.isGenerated } ->
            stringResource(R.string.parse_lyrics_ai_excluded)
        mode == SubtitleLyricsMode.ManualOnly -> stringResource(R.string.parse_lyrics_manual_unavailable)
        else -> stringResource(R.string.parse_lyrics_unavailable)
    }
}

@Composable
private fun subtitleLanguageName(subtitle: SubtitleInfo): String = when (
    subtitle.lan.lowercase(Locale.ROOT).removePrefix("ai-")
) {
    "zh", "zh-cn", "zh-hans" -> stringResource(R.string.parse_lyrics_language_simplified)
    "zh-tw", "zh-hant", "zh-hk" -> stringResource(R.string.parse_lyrics_language_traditional)
    // AI 来源已单独展示，不在语言名里重复“自动生成”。
    else -> subtitle.name.replace(Regex("[（(](?:AI|自动生成)[）)]", RegexOption.IGNORE_CASE), "").trim()
}
