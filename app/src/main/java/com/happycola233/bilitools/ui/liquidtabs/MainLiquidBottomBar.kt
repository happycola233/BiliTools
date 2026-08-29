package com.happycola233.bilitools.ui.liquidtabs

import android.view.View
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.ui.liquidglass.rememberViewLayerBackdrop
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.kyant.backdrop.backdrops.layerBackdrop

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
    // 采样层的底色需与页面底色一致，否则玻璃在内容空白处会折射出异色
    val backdropBaseColor = AppSurfaces.pageContainerColor
    val backdrop = rememberViewLayerBackdrop(backgroundView, backdropBaseColor)

    Box(modifier.fillMaxSize()) {
        // 采样层锚点：与整个浮层同尺寸，保证坐标系与 Activity 内容区对齐
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop))

        // 表层与下载页玻璃浮窗同配方：纯白/纯黑叠加，玻璃质感由折射与高光呈现
        val isLightTheme = !isSystemInDarkTheme()
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = MainLiquidTabs.size,
            // 气泡本身是透明玻璃透镜，这个颜色着色的是选中项的图标与文字，
            // 属于前景角色，必须用压得够深的 primary，浅色填充色在玻璃上读不出来
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
