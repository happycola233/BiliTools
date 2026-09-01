package com.happycola233.bilitools.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 强调色按承载方式分为两类，常规组件不要随意混用：
 *
 * - **固定填充面**（[fill]）：主按钮底、选中胶囊、深色开关开启轨道、滑条已选段等着色区域。
 *   取自 M3 的 fixed 色组，浅色与深色模式下是同一个颜色，其上的内容使用 [onFill]，
 *   因此设置页的色块所见即所得。
 * - **模式相关强调色**（`MaterialTheme.colorScheme.primary`）：默认用于正文里的链接、强调文字、强调图标，
 *   随模式变化，浅色模式下压得足够深以保证读得清；也可作为需要更强轮廓对比的浅色控件填充。
 *
 * 浅色模式下 [fill] 与页面底色的对比度不到 3:1，能不能用它取决于色块之上是否有足以表达状态的内容：
 * 按钮、胶囊、滑条都有文字、图标或位置明确的滑块承担状态表达；
 * 勾选框与开关是视觉例外：浅色模式使用 `primary` 填充，分别搭配白色对勾与 `onPrimary` 滑块，
 * 与高亮边框统一并保证对比度；
 * 深色模式仍使用 [fill] 与 [onFill]，保持原有观感；
 * 进度条没有滑块，进度全靠已完成段与轨道的反差来读，只能用前景色。
 *
 * 液态玻璃底栏是个容易看错的例子：它的选中气泡是透明玻璃透镜而非色块，被着色的其实是
 * 选中项的图标与文字，所以那里也要用前景色，不是 [fill]。
 * 普通底栏不经过本对象：View 样式直接沿用 Material 3 Expressive 的导航栏 token，
 * 让选中指示器、图标与文字各自使用对应的颜色角色。
 *
 * 悬浮按钮（FAB）浅色模式使用 [floatingActionContainer]，与次级操作保持同一强调层级；
 * 深色模式则回到 [fill]，避免 `secondaryContainer` 或 `primaryContainer` 这类暗色容器
 * 糊进深色页面里（实测只有 1.88:1）。下载页 FAB 菜单展开后的带文案操作项不跟随主菜单
 * 按钮取色：浅色模式使用更轻的 `primaryFixed`，深色模式继续使用 [fill]。
 *
 * 完整的参数取值、色域约束与实测对比度见 `docs/配色系统/README.md`。
 */
internal object AppAccents {
    val fill: Color
        @Composable get() = MaterialTheme.colorScheme.primaryFixedDim

    val onFill: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimaryFixed

    /**
     * 悬浮操作配色：浅色下与“复制字幕”等次级操作同色，深色下使用高对比度的固定填充色。
     * 深浅判定必须基于最终 [androidx.compose.material3.ColorScheme]，以兼容应用内主题覆盖系统设置。
     */
    val floatingActionContainer: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesDarkSurfaces()) primaryFixedDim else secondaryContainer
        }

    val onFloatingActionContainer: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesDarkSurfaces()) onPrimaryFixed else onSecondaryContainer
        }

    /** 下载页展开菜单项：浅色使用较轻的 fixed 容器，深色保留高对比度的固定填充色。 */
    val floatingActionMenuItemContainer: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            if (usesDarkSurfaces()) primaryFixedDim else primaryFixed
        }

    val onFloatingActionMenuItemContainer: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimaryFixed

    /** 主按钮配色，深浅模式同色 */
    @Composable
    fun filledButtonColors(): ButtonColors {
        return ButtonDefaults.buttonColors(containerColor = fill, contentColor = onFill)
    }

    /**
     * 分段按钮组配色：选中胶囊使用 [fill]。
     *
     * 未选中容器默认沿用 M3 默认值（当前为 `surfaceContainer`）；直接铺在页面底色上的按钮组需要
     * 传入 [containerColor]，否则未选中项会与页面底色重合。
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun toggleButtonColors(
        containerColor: Color = Color.Unspecified,
        contentColor: Color = Color.Unspecified,
    ): ToggleButtonColors {
        return ToggleButtonDefaults.toggleButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            checkedContainerColor = fill,
            checkedContentColor = onFill,
        )
    }

    /** 复选框的启用选中态：浅色与高亮边框同色，深色保留原有 fixed 填充配色。 */
    @Composable
    fun checkboxColors(): CheckboxColors {
        val colorScheme = MaterialTheme.colorScheme
        val darkSurfaces = colorScheme.usesDarkSurfaces()
        return CheckboxDefaults.colors(
            checkedColor = if (darkSurfaces) fill else colorScheme.primary,
            checkmarkColor = if (darkSurfaces) onFill else Color.White,
        )
    }

    /**
     * 开关配色。浅色模式的开启态使用 `primary` 轨道与 `onPrimary` 滑块，强化与卡片底的层次；
     * 深色模式继续使用 [fill] 轨道与 [onFill] 滑块。普通浅色与深色模式的关闭态轨道使用
     * `surfaceContainerHigh` 且不画描边；纯黑模式改用 `surfaceContainerHighest` 轨道与 `outline`
     * 描边，避免轨道和同为 `surfaceContainerHigh` 的卡片底重合。浅色模式关闭态滑块使用
     * `onPrimary`，深色模式使用 `outline`。
     * 禁用态保留 M3 的降强调配色，但关闭态同样不画描边。
     */
    @Composable
    fun switchColors(): SwitchColors {
        val colorScheme = MaterialTheme.colorScheme
        val darkSurfaces = colorScheme.usesDarkSurfaces()
        val pureBlackSurfaces = colorScheme.usesPureBlackSurfaces()
        val checkedThumbColor = if (darkSurfaces) onFill else colorScheme.onPrimary
        val checkedTrackColor = if (darkSurfaces) fill else colorScheme.primary
        val uncheckedThumbColor = if (darkSurfaces) colorScheme.outline else checkedThumbColor
        return SwitchDefaults.colors(
            checkedThumbColor = checkedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedThumbColor = uncheckedThumbColor,
            uncheckedTrackColor = if (pureBlackSurfaces) {
                colorScheme.surfaceContainerHighest
            } else {
                colorScheme.surfaceContainerHigh
            },
            uncheckedBorderColor = if (pureBlackSurfaces) colorScheme.outline else Color.Transparent,
            disabledUncheckedBorderColor = Color.Transparent,
        )
    }

    /**
     * 滑条的启用态配色。已选段使用 [fill]，未选段使用 `surfaceContainerHigh`；滑块按模式取色：
     * 浅色模式使用 [onFill]，避免滑块糊进近白卡片；深色模式使用 [fill]，
     * 让滑块保持明亮并与活动轨道形成连续的强调色。
     */
    @Composable
    fun sliderColors(): SliderColors {
        val darkSurfaces = MaterialTheme.colorScheme.usesDarkSurfaces()
        return SliderDefaults.colors(
            thumbColor = if (darkSurfaces) fill else onFill,
            activeTrackColor = fill,
            activeTickColor = onFill,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}
