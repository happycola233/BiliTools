package com.happycola233.bilitools.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.DownloadPreferenceGroup
import com.happycola233.bilitools.data.DownloadPreferenceMemorySettings

/** 每个可保留的分组在设置页的呈现；顺序与解析页「下载与导出」卡片自上而下一致。 */
private data class PreferenceGroupPresentation(
    val group: DownloadPreferenceGroup,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
)

private val preferenceGroupPresentations = listOf(
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.OutputType,
        R.drawable.ic_output_movie_24,
        R.string.settings_download_preference_group_output_type,
        R.string.settings_download_preference_group_output_type_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.StreamFormat,
        R.drawable.ic_stream_24,
        R.string.settings_download_preference_group_stream_format,
        R.string.settings_download_preference_group_stream_format_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.VideoQuality,
        R.drawable.ic_video_settings_24,
        R.string.settings_download_preference_group_video_quality,
        R.string.settings_download_preference_group_video_quality_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.AudioQuality,
        R.drawable.ic_graphic_eq_24,
        R.string.settings_download_preference_group_audio_quality,
        R.string.settings_download_preference_group_audio_quality_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.Embedding,
        R.drawable.ic_subtitles_24,
        R.string.settings_download_preference_group_embedding,
        R.string.settings_download_preference_group_embedding_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.MiscExports,
        R.drawable.ic_closed_caption_24,
        R.string.settings_download_preference_group_misc_exports,
        R.string.settings_download_preference_group_misc_exports_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.Nfo,
        R.drawable.ic_movie_info_24,
        R.string.settings_download_preference_group_nfo,
        R.string.settings_download_preference_group_nfo_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.Danmaku,
        R.drawable.ic_danmaku_24,
        R.string.settings_download_preference_group_danmaku,
        R.string.settings_download_preference_group_danmaku_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.Images,
        R.drawable.ic_image_24,
        R.string.settings_download_preference_group_images,
        R.string.settings_download_preference_group_images_desc,
    ),
    PreferenceGroupPresentation(
        DownloadPreferenceGroup.Opus,
        R.drawable.ic_article_24,
        R.string.settings_download_preference_group_opus,
        R.string.settings_download_preference_group_opus_desc,
    ),
)

/** 「下载」设置里入口行的一句话摘要。 */
@Composable
internal fun downloadPreferenceMemorySummary(settings: DownloadPreferenceMemorySettings): String {
    val remembered = preferenceGroupPresentations.filter { it.group in settings.groups }
    return when {
        !settings.enabled -> stringResource(R.string.settings_download_preference_memory_summary_off)
        remembered.isEmpty() -> stringResource(R.string.settings_download_preference_memory_summary_none)
        remembered.size == preferenceGroupPresentations.size ->
            stringResource(R.string.settings_download_preference_memory_summary_all)
        else -> {
            val titles = remembered.map { stringResource(it.titleRes) }
            stringResource(
                R.string.settings_download_preference_memory_summary_some,
                remembered.size,
                titles.joinToString(stringResource(R.string.runtime_list_separator)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadPreferenceMemoryScreen(
    settings: DownloadPreferenceMemorySettings,
    onSettingsChange: (DownloadPreferenceMemorySettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScaffold(
        title = stringResource(R.string.settings_download_preference_memory),
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
            item {
                ExpressiveSwitchListItem(
                    checked = settings.enabled,
                    iconRes = R.drawable.ic_manage_history_24,
                    title = stringResource(R.string.settings_download_preference_memory),
                    description = stringResource(R.string.settings_download_preference_memory_desc),
                    items = 1, index = 0,
                    onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_download_preference_memory_groups_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    // 总开关关闭时说明整页都不生效，而不是逐项变灰却不解释原因。
                    val motionScheme = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = settings.enabled,
                        transitionSpec = {
                            fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                                fadeOut(motionScheme.fastEffectsSpec())
                        },
                        label = "downloadPreferenceMemoryGroupsHint",
                    ) { enabled ->
                        Text(
                            stringResource(
                                if (enabled) R.string.settings_download_preference_memory_groups_desc
                                else R.string.settings_download_preference_memory_disabled_hint,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
            preferenceGroupPresentations.forEachIndexed { index, presentation ->
                item(key = presentation.group.value) {
                    ExpressiveSwitchListItem(
                        checked = presentation.group in settings.groups,
                        enabled = settings.enabled,
                        iconRes = presentation.iconRes,
                        title = stringResource(presentation.titleRes),
                        description = stringResource(presentation.descriptionRes),
                        items = preferenceGroupPresentations.size,
                        index = index,
                        onCheckedChange = { checked ->
                            val groups = if (checked) settings.groups + presentation.group else settings.groups - presentation.group
                            onSettingsChange(settings.copy(groups = groups))
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
