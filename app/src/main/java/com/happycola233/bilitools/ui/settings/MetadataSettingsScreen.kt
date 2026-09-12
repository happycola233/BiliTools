package com.happycola233.bilitools.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.DownloadMetadataSettings
import com.happycola233.bilitools.data.SubtitleLyricsMode
import com.happycola233.bilitools.ui.LyricsLanguageExplanationContent
import com.happycola233.bilitools.ui.theme.AppSurfaces

@Composable
internal fun MetadataSettingsScreen(
    settings: AppSettings,
    onSettingsChange: (DownloadMetadataSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = settings.metadata
    SettingsScaffold(
        title = stringResource(R.string.settings_metadata_options),
        subtitle = stringResource(R.string.settings_download_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }
            if (!settings.addMetadata) {
                item {
                    Text(
                        stringResource(R.string.settings_metadata_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            item {
                ExpressiveSwitchListItem(
                    checked = metadata.embedCover,
                    enabled = settings.addMetadata,
                    iconRes = R.drawable.ic_image_24,
                    title = stringResource(R.string.settings_metadata_cover),
                    description = stringResource(R.string.settings_metadata_cover_desc),
                    items = 1, index = 0,
                    onCheckedChange = { onSettingsChange(metadata.copy(embedCover = it)) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item { LyricsSettingsGroup(settings, onSettingsChange) }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ExpressiveSwitchListItem(
                    checked = metadata.useUploaderAsArtist,
                    enabled = settings.addMetadata,
                    iconRes = R.drawable.ic_artist_24,
                    title = stringResource(R.string.settings_metadata_uploader),
                    description = stringResource(R.string.settings_metadata_uploader_desc),
                    items = 1, index = 0,
                    onCheckedChange = { onSettingsChange(metadata.copy(useUploaderAsArtist = it)) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ExpressiveSwitchListItem(
                    checked = metadata.useCollectionAsAlbum,
                    enabled = settings.addMetadata,
                    iconRes = R.drawable.ic_album_24,
                    title = stringResource(R.string.settings_metadata_collection),
                    description = stringResource(R.string.settings_metadata_collection_desc),
                    items = 1, index = 0,
                    onCheckedChange = { onSettingsChange(metadata.copy(useCollectionAsAlbum = it)) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSettingsGroup(
    settings: AppSettings,
    onSettingsChange: (DownloadMetadataSettings) -> Unit,
) {
    val metadata = settings.metadata
    val enabled = settings.addMetadata && metadata.embedLyrics
    // 原始歌词和字幕转写共用开关；同一容器中的子选项保留偏好，关闭父开关时停止生效。
    Surface(shape = MaterialTheme.shapes.largeIncreased, color = AppSurfaces.cardContainerColor) {
        Column {
            ExpressiveSwitchListItem(
                checked = metadata.embedLyrics,
                enabled = settings.addMetadata,
                iconRes = R.drawable.ic_lyrics_24,
                title = stringResource(R.string.settings_metadata_lyrics),
                description = stringResource(R.string.settings_metadata_lyrics_desc),
                items = 1, index = 0,
                onCheckedChange = { onSettingsChange(metadata.copy(embedLyrics = it)) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.settings_metadata_subtitles),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LyricsSupportingText(stringResource(R.string.settings_metadata_subtitles_desc))
                ConnectedToggleButtons(
                    options = SubtitleLyricsMode.entries,
                    selected = metadata.subtitleLyrics,
                    enabled = enabled,
                    onSelect = { onSettingsChange(metadata.copy(subtitleLyrics = it)) },
                    modifier = Modifier.selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) { mode ->
                    Text(
                        stringResource(when (mode) {
                            SubtitleLyricsMode.Off -> R.string.settings_metadata_subtitles_off
                            SubtitleLyricsMode.ManualOnly -> R.string.settings_metadata_subtitles_manual
                            SubtitleLyricsMode.PreferManual -> R.string.settings_metadata_subtitles_ai
                        }),
                        textAlign = TextAlign.Center,
                    )
                }
                LyricsSupportingText(stringResource(when {
                    !enabled -> R.string.settings_metadata_lyrics_disabled
                    metadata.subtitleLyrics == SubtitleLyricsMode.Off -> R.string.settings_metadata_subtitles_off_desc
                    metadata.subtitleLyrics == SubtitleLyricsMode.ManualOnly -> R.string.settings_metadata_subtitles_manual_desc
                    else -> R.string.settings_metadata_subtitles_ai_desc
                }))
                LyricsLanguageExplanation()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsLanguageExplanation() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lyricsLanguageExplanation",
    )
    val expandedDescription = stringResource(
        if (expanded) R.string.settings_metadata_lyrics_language_expanded
        else R.string.settings_metadata_lyrics_language_collapsed,
    )
    // 宽度与文字位置保持不变，只从顶部揭示内容；段前间距随内容一起收起，避免退出后再跳 8dp。
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = expandedDescription },
        ) {
            Text(stringResource(R.string.settings_metadata_lyrics_language))
            Spacer(Modifier.width(8.dp))
            SettingsItemIcon(R.drawable.ic_expand_more_24, Modifier.rotate(rotation))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            ) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            LyricsLanguageExplanationContent(Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun LyricsSupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
