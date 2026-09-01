package com.happycola233.bilitools.ui.liquidtabs

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.usesDarkSurfaces
import com.kyant.backdrop.backdrops.LayerBackdrop

private data class MainTabSpec(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
)

private val MainTabs = listOf(
    MainTabSpec(R.drawable.ic_home_24, R.string.nav_parse),
    MainTabSpec(R.drawable.ic_download_for_offline_24, R.string.nav_downloads),
    MainTabSpec(R.drawable.ic_account_circle_24, R.string.nav_me),
)

/**
 * 主界面液态玻璃底栏。
 *
 * 底栏与页面位于同一棵 Compose 树中，直接使用主壳内容层的 [backdrop] 采样。
 */
@Composable
fun MainLiquidBottomBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: LayerBackdrop,
    glassStyle: LiquidGlassStyle,
    surfaceAlpha: Float,
    widthFraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // 表层与下载页玻璃浮窗同配方：纯白/纯黑叠加，玻璃质感由折射与高光呈现
        val isLightTheme = !MaterialTheme.colorScheme.usesDarkSurfaces()
        LiquidBottomTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = MainTabs.size,
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
            MainTabs.forEachIndexed { index, tab ->
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

/** 与旧 BottomNavigationView 等价的 Material 3 主导航栏。 */
@Composable
fun MainMaterialBottomBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = selectedTabIndex()
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = AppSurfaces.pageContainerColor,
        tonalElevation = 0.dp,
    ) {
        MainTabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(tab.labelRes),
                        maxLines = 1,
                    )
                },
                alwaysShowLabel = true,
            )
        }
    }
}
