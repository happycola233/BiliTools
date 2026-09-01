package com.happycola233.bilitools.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FloatingControlsDefaults {
    val EdgePadding: Dp = 16.dp
    // 旧 CoordinatorLayout 的滚动页在展开态会向屏幕底部多延伸 40dp，56dp 净空中
    // 实际只有 16dp 位于屏幕内。主壳改为固定全屏后不再有这段延伸，因此直接使用边缘净空，
    // 才能让按钮在折叠、展开与 snap 全程保持原来的屏幕坐标。
    val MainScreenBottomPadding: Dp = EdgePadding
    // 外部分享面板从未经过 CoordinatorLayout 的 40dp 滚动范围，继续沿用原来的底部留白。
    val ExternalEntryBottomPadding: Dp = 56.dp
    val DownloadsListBottomPadding: Dp = 88.dp

    fun menuFabBottomPadding(bottomPadding: Dp = MainScreenBottomPadding): Dp {
        return (bottomPadding - EdgePadding).coerceAtLeast(0.dp)
    }
}
