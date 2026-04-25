package com.happycola233.bilitools.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FloatingControlsDefaults {
    val EdgePadding: Dp = 16.dp
    val BottomPadding: Dp = 56.dp
    val ListBottomPadding: Dp = 88.dp

    fun menuFabBottomPadding(bottomPadding: Dp = BottomPadding): Dp {
        return (bottomPadding - EdgePadding).coerceAtLeast(0.dp)
    }
}
