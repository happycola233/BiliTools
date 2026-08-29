package com.happycola233.bilitools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 全站统一的表面配色语义层，把界面分为三层深度：
 *
 * 1. [pageContainerColor]：页面底色，托住上面的卡片；
 * 2. [cardContainerColor]：卡片与列表项底色，浮在页面之上；
 * 3. [insetContainerColor]：卡片内嵌区域（输入框、下拉框、次级列表、展开的子项）底色，沉在卡片之下。
 *
 * 深色配色下容器色阶随层级变亮，方向与浅色相反；纯黑深色模式又把 surface 与 background 压到了
 * #000000，标准色阶不足以拉开上述层次。因此每层都按浅色、深色、纯黑分别取相邻档位，
 * 保证三种模式下层与层的间距观感一致。
 *
 * 界面代码只应该用这三个语义名，不要直接取 `surfaceContainer*`——浅色模式的档位分配是
 * 被卡片底的色域天花板逼出来的（内嵌层刻意沉到了页面底之下），照直觉挑档会踩坑。
 * 来历见 `docs/配色系统/README.md` 第四节。
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

    /** 只比 [cardContainerColor] 沉一档，刚好读出内嵌关系又不至于像在卡片上挖了个洞。 */
    val insetContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            when {
                usesPureBlackSurfaces() -> surfaceContainer
                usesDarkSurfaces() -> surfaceContainerHigh
                else -> surfaceContainerLow
            }
        }

    /**
     * 内嵌控件的展开/激活态底色，在 [insetContainerColor] 之上再抬一档以强调当前焦点。
     *
     * 浅色下刻意不取 `surfaceContainer`：那是页面底色，为了淡雅被抬得离 `surfaceContainerLow` 很近，
     * 焦点态会读不出来。
     */
    val insetActiveContainerColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            when {
                usesPureBlackSurfaces() -> surfaceContainerHigh
                usesDarkSurfaces() -> surfaceContainerHighest
                else -> surfaceContainerHigh
            }
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
