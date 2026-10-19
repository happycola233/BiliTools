package com.happycola233.bilitools.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.happycola233.bilitools.R

/** 关于页「检查更新」一行的展示状态，由设置页根据检查进度与最近一次检查结果合成。 */
sealed interface UpdateCheckUiState {
    /** 本次启动后还没有任何检查结果。 */
    data object Idle : UpdateCheckUiState

    data object Checking : UpdateCheckUiState

    data object UpToDate : UpdateCheckUiState

    data class UpdateAvailable(val tagName: String) : UpdateCheckUiState

    data object Failed : UpdateCheckUiState
}

/**
 * 状态说明在切换时淡入淡出，发现新版本与检查失败分别用强调色与错误色点出。
 *
 * 不要改成上下滑动：Expressive 的 ListItem 靠说明文字首末基线是否一致判断单行还是多行，
 * 滑动途中新旧两行错开会被当成多行，整行切到三行高度后不再回落。淡入淡出时两行始终重叠在同一基线上。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UpdateCheckStatusText(
    state: UpdateCheckUiState,
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = state,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "updateCheckStatusText",
        // 整行可点击时语义会合并到行上，状态变化由读屏随之播报。
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) { target ->
        val colorScheme = MaterialTheme.colorScheme
        val text = when (target) {
            UpdateCheckUiState.Idle -> stringResource(R.string.settings_about_version, versionName)
            UpdateCheckUiState.Checking -> stringResource(R.string.settings_check_update_desc_checking)
            UpdateCheckUiState.UpToDate -> stringResource(R.string.settings_check_update_desc_latest)
            is UpdateCheckUiState.UpdateAvailable -> {
                stringResource(R.string.settings_check_update_desc_available, target.tagName)
            }
            UpdateCheckUiState.Failed -> stringResource(R.string.settings_check_update_desc_failed)
        }
        Text(
            text = text,
            color = when (target) {
                is UpdateCheckUiState.UpdateAvailable -> colorScheme.primary
                UpdateCheckUiState.Failed -> colorScheme.error
                else -> Color.Unspecified
            },
            fontWeight = if (target is UpdateCheckUiState.UpdateAvailable) FontWeight.Medium else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
