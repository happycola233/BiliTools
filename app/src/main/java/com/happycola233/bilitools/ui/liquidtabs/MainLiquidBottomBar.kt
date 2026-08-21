package com.happycola233.bilitools.ui.liquidtabs

import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private data class LiquidTabSpec(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
)

private val MainLiquidTabs = listOf(
    LiquidTabSpec(R.drawable.ic_home_24, R.string.nav_parse),
    LiquidTabSpec(R.drawable.ic_download_for_offline_24, R.string.nav_downloads),
    LiquidTabSpec(R.drawable.ic_account_circle_24, R.string.nav_me),
)

/**
 * 主界面液态玻璃底栏。
 *
 * 底栏是叠在 View 层之上的全屏 Compose 浮层，玻璃需要采样到身后 ViewPager2 的实时画面：
 * 把 [backgroundView] 手动绘制进 LayerBackdrop 的采样层（硬件加速下子 View 以 RenderNode
 * 引用形式录制），并用 OnPreDrawListener 监听 View 内容失效（isDirty）或位移，
 * 变化时递增版本号触发采样层重录制，保证玻璃内容与页面实时同步。
 */
@Composable
fun MainLiquidBottomBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backgroundView: View,
    glassStyle: LiquidGlassStyle,
    surfaceAlpha: Float,
    widthFraction: Float,
    modifier: Modifier = Modifier,
) {
    val contentVersion = remember { mutableIntStateOf(0) }
    DisposableEffect(backgroundView) {
        var lastX = backgroundView.x
        var lastY = backgroundView.y
        val listener = ViewTreeObserver.OnPreDrawListener {
            val moved = backgroundView.x != lastX || backgroundView.y != lastY
            if (moved || backgroundView.isDirty) {
                lastX = backgroundView.x
                lastY = backgroundView.y
                contentVersion.intValue++
            }
            true
        }
        backgroundView.viewTreeObserver.addOnPreDrawListener(listener)
        onDispose {
            backgroundView.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onDrawBackdrop: ContentDrawScope.() -> Unit = remember(backgroundView, surfaceColor) {
        {
            contentVersion.intValue // 读取版本号以订阅 View 内容变化
            drawRect(surfaceColor)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val checkpoint = native.save()
                native.translate(backgroundView.x, backgroundView.y)
                backgroundView.draw(native)
                native.restoreToCount(checkpoint)
            }
        }
    }
    val backdrop = rememberLayerBackdrop(onDraw = onDrawBackdrop)

    Box(modifier.fillMaxSize()) {
        // 采样层锚点：与整个浮层同尺寸，保证坐标系与 Activity 内容区对齐
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))

        // 表层与下载页批量管理玻璃面板同配方：纯白/纯黑叠加，玻璃质感由折射与高光呈现
        val isLightTheme = !isSystemInDarkTheme()
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = MainLiquidTabs.size,
            accentColor = MaterialTheme.colorScheme.primary,
            containerColor = (if (isLightTheme) Color.White else Color.Black)
                .copy(alpha = surfaceAlpha.coerceIn(0f, 1f)),
            glassStyle = glassStyle,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth(widthFraction.coerceIn(0.5f, 1f)),
        ) {
            val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            MainLiquidTabs.forEachIndexed { index, tab ->
                LiquidBottomTab(onClick = { onTabSelected(index) }) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(tab.labelRes),
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
