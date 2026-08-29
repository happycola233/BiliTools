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
 * 强调色的两种用法，二者不可互换：
 *
 * - **填充面**（[fill]）：主按钮底、选中胶囊、勾选框、普通底栏的选中气泡这类着色区域。
 *   取自 M3 的 fixed 色组，浅色与深色模式下是同一个颜色，因此设置页的色块所见即所得。
 * - **前景色**（`MaterialTheme.colorScheme.primary`）：正文里的链接、强调文字、强调图标。
 *   随模式变化，浅色模式下压得足够深以保证读得清。
 *
 * [fill] 与页面底色的对比度不到 3:1，能不能用它取决于**色块之上有没有能承担状态表达的深色元素**：
 * 按钮、胶囊、勾选框、开关、滑条的着色区域上都压着深色内容（文字、对勾、滑块），
 * 状态一眼可辨，一律用 [fill]，这样整个界面的强调色只有一种视觉印象；
 * 进度条没有滑块，进度全靠已完成段与轨道的反差来读，只能用前景色。
 *
 * 液态玻璃底栏是个容易看错的例子：它的选中气泡是透明玻璃透镜而非色块，被着色的其实是
 * 选中项的图标与文字，所以那里也要用前景色，不是 [fill]。
 *
 * 悬浮按钮（FAB）必须用 [fill]：它浮在页面底之上，而 [fill] 深浅同值、始终是浅色，
 * 两个模式下都浮得起来。深色模式下若改用 `secondaryContainer` 或 `primaryContainer`
 * 这类容器色，它们本身是暗色，会直接糊进深色页面里（实测只有 1.88:1）。
 *
 * 完整的参数取值、色域约束与实测对比度见 `docs/配色系统/README.md`。
 */
internal object AppAccents {
    val fill: Color
        @Composable get() = MaterialTheme.colorScheme.primaryFixedDim

    val onFill: Color
        @Composable get() = MaterialTheme.colorScheme.onPrimaryFixed

    /** 主按钮配色，深浅模式同色 */
    @Composable
    fun filledButtonColors(): ButtonColors {
        return ButtonDefaults.buttonColors(containerColor = fill, contentColor = onFill)
    }

    /** 分段按钮组的选中胶囊配色，未选中态沿用 M3 默认的低强调容器 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun toggleButtonColors(): ToggleButtonColors {
        return ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = fill,
            checkedContentColor = onFill,
        )
    }

    /** 勾选框配色，对勾用 [onFill] 压在填充面上 */
    @Composable
    fun checkboxColors(): CheckboxColors {
        return CheckboxDefaults.colors(checkedColor = fill, checkmarkColor = onFill)
    }

    /**
     * 开关配色。开启态轨道一律用 [fill]，与同屏的选中胶囊同色；滑块按模式取色：
     *
     * - 浅色模式取白色圆片，即开关这个控件固有的样子。白滑块与浅轨道的明度差不足 2:1，
     *   靠滑块里那枚 [onFill] 对勾把边界交代清楚。
     * - 深色模式取 [onFill]，白滑块在这里会连同周围的浅轨道一起糊成一整块亮斑，
     *   反过来压成深色才读得出滑块的位置。
     */
    @Composable
    fun switchColors(): SwitchColors {
        val darkSurfaces = MaterialTheme.colorScheme.usesDarkSurfaces()
        return SwitchDefaults.colors(
            checkedThumbColor = if (darkSurfaces) onFill else Color.White,
            checkedTrackColor = fill,
            checkedIconColor = if (darkSurfaces) fill else onFill,
        )
    }

    /**
     * 滑条配色。已选段用 [fill]，未选段退成中性容器。
     * 滑块压成 [onFill] 而非跟开关一样取白色：滑条的两条轨道都很细，
     * 白滑块会连同近白的卡片底一起糊成一片，读不出当前值落在哪。
     */
    @Composable
    fun sliderColors(): SliderColors {
        return SliderDefaults.colors(
            thumbColor = onFill,
            activeTrackColor = fill,
            activeTickColor = onFill,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}
