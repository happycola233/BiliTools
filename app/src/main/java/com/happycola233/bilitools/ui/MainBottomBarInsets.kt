package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 主界面底部导航区（液态玻璃底栏 / Material 底栏）占据的高度，不含系统安全边距。
 *
 * 主界面三个页面全出血绘制到屏幕底边，内容从底栏后方滚过（液态玻璃依赖这一点采样真实内容），
 * 因此页面的滚动容器与底部悬浮控件需要自行预留这块净空，页面容器本身始终保持全屏。
 */
val MainBottomBarHeight: Dp = 80.dp

/**
 * 主界面底部控件共用的系统安全边距。
 *
 * 小窗的窗口装饰与底部拖动手势区不一定属于 navigationBars，需一并避让；
 * union 对重叠区域取最大值，避免重复留白。仅合并底部强制手势区，避免侧边返回手势挤压底栏。
 * 不包含 IME，键盘避让仍由各页面负责。
 */
@Composable
fun mainBottomBarWindowInsets(): WindowInsets =
    WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .union(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

/** 主界面页面内容需预留的底部净空：底栏高度 + 系统安全边距。 */
@Composable
fun mainBottomBarBottomInset(): Dp =
    MainBottomBarHeight + mainBottomBarWindowInsets().asPaddingValues().calculateBottomPadding()
