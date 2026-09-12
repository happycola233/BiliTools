package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.theme.AppSurfaces

/** 设置页和解析页共用正文，保证两个入口的歌词说明始终一致。 */
@Composable
internal fun LyricsLanguageExplanationContent(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.settings_metadata_lyrics_language_single,
            R.string.settings_metadata_lyrics_language_selected,
            R.string.settings_metadata_lyrics_language_auto,
            R.string.settings_metadata_lyrics_language_original,
        ).forEach { paragraph ->
            Text(
                stringResource(paragraph),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LyricsLanguageHelpButton(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_metadata_lyrics_language)
    val menuWidth = minOf(320.dp, LocalConfiguration.current.screenWidthDp.dp - 32.dp)
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_help_24),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // 复用 Material 菜单的锚点定位、进出动画和滚动能力。
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth).semantics { paneTitle = title },
            shape = MaterialTheme.shapes.large,
            containerColor = AppSurfaces.floatingPanelContainerColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                LyricsLanguageExplanationContent()
            }
        }
    }
}
