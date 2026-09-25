package com.happycola233.bilitools.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.happycola233.bilitools.R

/**
 * 删除操作的固定容器配色，与独立图标的中性色、失败提示的错误色分开。
 *
 * 复用配色生成器产出的深色错误色板：操作背景使用浅粉色，其上的内容搭配深红色。
 * 独立删除图标使用 `MaterialTheme.colorScheme.onSurfaceVariant`，与列表里的其他操作图标保持一致。
 * 这里直接读取色板资源，手动配色、动态取色、浅色与深色模式均保持一致；不覆盖全局错误角色。
 */
internal object AppDestructiveColors {
    val container: Color
        @Composable get() = colorResource(R.color.md_theme_dark_error)

    val onContainer: Color
        @Composable get() = colorResource(R.color.md_theme_dark_onError)
}
