package com.happycola233.bilitools.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.happycola233.bilitools.R

/**
 * 删除操作的固定容器配色，与独立图标的中性色、失败提示的错误色分开。
 *
 * 复用配色生成器产出的基线错误色板：清除记录为浅粉色，删除文件为深红色。
 * 独立删除图标使用 `MaterialTheme.colorScheme.onSurfaceVariant`，与列表里的其他操作图标保持一致。
 * 这里直接读取色板资源，手动配色、动态取色、浅色与深色模式均保持一致；不覆盖全局错误角色。
 */
internal object AppDestructiveColors {
    val container: Color
        @Composable get() = colorResource(R.color.md_theme_dark_error)

    val onContainer: Color
        @Composable get() = colorResource(R.color.md_theme_dark_onError)

    // 删除文件使用更深的红色，与清除记录区分；两种模式均使用同一组高对比度前景。
    val strongContainer: Color
        @Composable get() = colorResource(R.color.md_theme_light_error)

    val onStrongContainer: Color
        @Composable get() = colorResource(R.color.md_theme_light_onError)
}
