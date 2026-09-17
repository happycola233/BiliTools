package com.happycola233.bilitools.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.DownloadMetadataSettings

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
            item {
                ExpressiveSwitchListItem(
                    checked = metadata.useUploaderAsArtist,
                    enabled = settings.addMetadata,
                    iconRes = R.drawable.ic_artist_24,
                    title = stringResource(R.string.settings_metadata_uploader),
                    description = stringResource(R.string.settings_metadata_uploader_desc),
                    items = 2, index = 0,
                    onCheckedChange = { onSettingsChange(metadata.copy(useUploaderAsArtist = it)) },
                )
            }
            item {
                ExpressiveSwitchListItem(
                    checked = metadata.useCollectionAsAlbum,
                    enabled = settings.addMetadata,
                    iconRes = R.drawable.ic_album_24,
                    title = stringResource(R.string.settings_metadata_collection),
                    description = stringResource(R.string.settings_metadata_collection_desc),
                    items = 2, index = 1,
                    onCheckedChange = { onSettingsChange(metadata.copy(useCollectionAsAlbum = it)) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    stringResource(R.string.settings_metadata_embedding_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
