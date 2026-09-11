package com.happycola233.bilitools.ui.parse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** 在输出选择旁展示实际歌词策略，与入队时共用语言选择，避免说明与下载行为分叉。 */
@Composable
internal fun ParseLyricsSummary(
    state: ParseUiState,
    settings: DownloadMetadataSettings?,
    modifier: Modifier = Modifier,
) {
    if (state.outputType != OutputType.AudioOnly) return
    val types = state.selectedItemIndices.mapNotNull { state.items.getOrNull(it)?.type }.toSet()
    val summary = when {
        settings == null -> stringResource(R.string.parse_lyrics_metadata_off)
        !settings.embedLyrics -> stringResource(R.string.parse_lyrics_off)
        else -> buildList {
            if (MediaType.Music in types) add(stringResource(R.string.parse_lyrics_original))
            if (types.any { it == MediaType.Video || it == MediaType.Bangumi || it == MediaType.Lesson }) {
                add(subtitleLyricsSummary(state, settings.subtitleLyrics))
            }
        }.joinToString("\n")
    }
    if (summary.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Icon(
            painterResource(R.drawable.ic_lyrics_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.parse_lyrics_title), style = MaterialTheme.typography.labelLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun subtitleLyricsSummary(state: ParseUiState, mode: SubtitleLyricsMode): String {
    if (mode == SubtitleLyricsMode.Off) return stringResource(R.string.parse_lyrics_subtitles_off)
    val language = state.preferredLyricsSubtitleLanguage
    if (language != null) {
        val subtitle = state.subtitleList.firstOrNull { it.lan == language }
        return stringResource(
            when {
                mode == SubtitleLyricsMode.PreferManual -> R.string.parse_lyrics_selected_ai
                subtitle?.isGenerated == true -> R.string.parse_lyrics_selected_ai_excluded
                else -> R.string.parse_lyrics_selected_manual
            },
            subtitle?.name ?: language,
        )
    }
    return stringResource(
        if (mode == SubtitleLyricsMode.ManualOnly) R.string.parse_lyrics_auto_manual
        else R.string.parse_lyrics_auto_ai,
    )
}
