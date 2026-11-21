package com.happycola233.bilitools.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.ui.SubpageScaffold

/** 连点版本号的结果，提示语与 Android 系统的「开发者选项」保持一致。 */
internal sealed interface DeveloperTapOutcome {
    data object None : DeveloperTapOutcome
    data class StepsAway(val steps: Int) : DeveloperTapOutcome
    data object Unlocked : DeveloperTapOutcome
    data object AlreadyEnabled : DeveloperTapOutcome
}

/** 与系统设置一样：连点 7 次开启，第 3 次起提示还差几次。两次点按间隔过久则重新计数。 */
internal class DeveloperOptionsTapCounter {
    private var taps = 0
    private var lastTapAt = Long.MIN_VALUE

    fun tap(now: Long, alreadyEnabled: Boolean): DeveloperTapOutcome {
        if (alreadyEnabled) return DeveloperTapOutcome.AlreadyEnabled
        taps = if (lastTapAt != Long.MIN_VALUE && now - lastTapAt <= TAP_GAP_MILLIS) taps + 1 else 1
        lastTapAt = now
        return when {
            taps >= TAPS_TO_UNLOCK -> {
                taps = 0
                DeveloperTapOutcome.Unlocked
            }
            taps >= FIRST_HINT_TAP -> DeveloperTapOutcome.StepsAway(TAPS_TO_UNLOCK - taps)
            else -> DeveloperTapOutcome.None
        }
    }

    companion object {
        const val TAPS_TO_UNLOCK = 7
        private const val FIRST_HINT_TAP = 3
        private const val TAP_GAP_MILLIS = 1_500L
    }
}

/** 连点时新提示立刻顶掉旧提示，不在屏幕上排队。 */
internal class ToastHolder {
    private var current: Toast? = null

    fun show(context: Context, text: String) {
        current?.cancel()
        current = Toast.makeText(context, text, Toast.LENGTH_SHORT).also(Toast::show)
    }
}

@Composable
internal fun DeveloperSettingsScreen(
    settings: AppSettings,
    onDeveloperOptionsChange: (Boolean) -> Unit,
    onGlassDebugChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubpageScaffold(
        title = stringResource(R.string.settings_developer_title),
        subtitle = stringResource(R.string.settings_screen_title),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = innerPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(14.dp)) }
            item {
                ExpressiveSwitchListItem(
                    checked = settings.developerOptionsEnabled,
                    iconRes = R.drawable.ic_code_24,
                    title = stringResource(R.string.settings_developer_toggle),
                    description = stringResource(R.string.settings_developer_toggle_desc),
                    items = 1,
                    index = 0,
                    onCheckedChange = onDeveloperOptionsChange,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                ExpressiveSwitchListItem(
                    checked = settings.downloadsGlassDebugEnabled,
                    iconRes = R.drawable.ic_blur_on_24,
                    title = stringResource(R.string.settings_downloads_glass_debug),
                    description = stringResource(R.string.settings_downloads_glass_debug_desc),
                    items = 1,
                    index = 0,
                    onCheckedChange = onGlassDebugChange,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}
