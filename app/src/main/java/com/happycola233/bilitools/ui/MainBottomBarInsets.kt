package com.happycola233.bilitools.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 主界面底部导航区（液态玻璃底栏 / Material 底栏）占据的高度，不含系统导航栏 inset。
 *
 * 主界面三个页面全出血绘制到屏幕底边，内容从底栏后方滚过（液态玻璃依赖这一点采样真实内容），
 * 因此页面的滚动容器与底部悬浮控件需要自行预留这块净空，页面容器本身始终保持全屏。
 */
val MainBottomBarHeight: Dp = 80.dp

/** 主界面页面内容需预留的底部净空：底栏高度 + 系统导航栏 inset。 */
@Composable
fun mainBottomBarBottomInset(): Dp =
    MainBottomBarHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
