package com.happycola233.bilitools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 全站统一的表面配色语义层，把界面分为四层深度：
 *
 * 1. [pageContainerColor]：页面底色，托住上面的卡片；
 * 2. [cardContainerColor]：卡片与列表项底色，浮在页面之上；
 * 3. [insetContainerColor]：卡片内嵌区域（输入框、下拉框、次级列表、展开的子项）底色，介于卡片与页面之间；
 * 4. [modalContainerColor]：对话框等模态容器底色，位于当前内容之上。
 *
 * 表面梯度沿用 Material 3 Expressive 的档位（浅色卡片 T98 / 内嵌 T95 / 页面 T94 / 激活与模态 T90，
 * 深色卡片 T18 / 内嵌 T12 / 页面 T9 / 激活与模态 T15），与系统动态取色下的结构一致。
 * 纯黑深色模式把 surface 与 background 压到了 #000000，标准色阶不足以拉开层次，因此单独取相邻档位。
 *
 * 界面代码只应该用这几个语义名，不要直接取 `surfaceContainer*`。来历见 `docs/配色系统/README.md` 第四节。
 */
internal object AppSurfaces {
    val pageContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesPureBlackSurfaces()) surface else surfaceContainer
        }

    val cardContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesPureBlackSurfaces()) surfaceContainerHigh else surfaceBright
        }

    /** 模态容器取最高容器色阶，纯黑模式下也不会与同为 `surfaceContainerHigh` 的卡片重合。 */
    val modalContainerColor: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHighest

    /**
     * 浅色取 `surfaceContainerLow`（T96）与 `surfaceContainer`（T94）的中点：T96 对 T98 卡片只有 1.05:1，
     * 内嵌区域几乎看不出；T94 又在近白卡片上压出一块明显的灰，中点 T95（约 1.07:1）刚好读作一层轻凹陷，
     * 输入框另有 `outlineVariant` 描边勾出边界。深色取 `surfaceContainerHigh`（T12，对 T18 卡片 1.17:1），
     * 比页面 T9 略浮、比卡片沉，方向与浅色一致。纯黑取 `surfaceContainer`（#101010）。
     */
    val insetContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            when {
                usesPureBlackSurfaces() -> surfaceContainer
                usesDarkSurfaces() -> surfaceContainerHigh
                else -> lerp(surfaceContainerLow, surfaceContainer, 0.5f)
            }
        }

    /**
     * 内嵌控件的展开/激活态底色，在 [insetContainerColor] 之上再拉开一档以强调当前焦点
     * （浅色 T95 → T90，深色 T12 → T15，纯黑 #101010 → #171717）。
     */
    val insetActiveContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesPureBlackSurfaces()) surfaceContainerHigh else surfaceContainerHighest
        }
}

/** 判断当前是否为纯黑深色模式。配色由 XML 主题叠加而来，这里以最终颜色值反推，保证 View 层与 Compose 层判定一致。 */
internal fun ColorScheme.usesPureBlackSurfaces(): Boolean {
    return surface == Color.Black && background == Color.Black
}

/**
 * 判断当前是否为深色配色：深色下层级越高的容器色越亮，浅色则越暗。
 *
 * 不要改用 `isSystemInDarkTheme()`——应用内的主题模式设置可以覆盖系统深浅，那个 API 会判错。
 * 这里从最终色值反推，与 View 层看到的是同一套配色。
 */
internal fun ColorScheme.usesDarkSurfaces(): Boolean {
    return surfaceContainerHighest.luminance() > surface.luminance()
}
