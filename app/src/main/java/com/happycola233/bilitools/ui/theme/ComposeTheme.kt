package com.happycola233.bilitools.ui.theme

import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import com.google.android.material.color.MaterialColors

@Composable
fun rememberAndroidThemeColorScheme(): ColorScheme {
    val view = LocalView.current
    val isDarkTheme = isSystemInDarkTheme()

    return remember(view, isDarkTheme) {
        val baseScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
        baseScheme.copy(
            primary = view.resolveThemeColor(
                androidx.appcompat.R.attr.colorPrimary,
                baseScheme.primary,
            ),
            onPrimary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnPrimary,
                baseScheme.onPrimary,
            ),
            primaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorPrimaryContainer,
                baseScheme.primaryContainer,
            ),
            onPrimaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnPrimaryContainer,
                baseScheme.onPrimaryContainer,
            ),
            secondary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSecondary,
                baseScheme.secondary,
            ),
            onSecondary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSecondary,
                baseScheme.onSecondary,
            ),
            secondaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSecondaryContainer,
                baseScheme.secondaryContainer,
            ),
            onSecondaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSecondaryContainer,
                baseScheme.onSecondaryContainer,
            ),
            tertiary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorTertiary,
                baseScheme.tertiary,
            ),
            onTertiary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnTertiary,
                baseScheme.onTertiary,
            ),
            tertiaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorTertiaryContainer,
                baseScheme.tertiaryContainer,
            ),
            onTertiaryContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnTertiaryContainer,
                baseScheme.onTertiaryContainer,
            ),
            background = view.resolveThemeColor(android.R.attr.colorBackground, baseScheme.background),
            onBackground = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnBackground,
                baseScheme.onBackground,
            ),
            surface = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurface,
                baseScheme.surface,
            ),
            onSurface = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurface,
                baseScheme.onSurface,
            ),
            surfaceVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceVariant,
                baseScheme.surfaceVariant,
            ),
            onSurfaceVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                baseScheme.onSurfaceVariant,
            ),
            surfaceTint = view.resolveThemeColor(
                androidx.appcompat.R.attr.colorPrimary,
                baseScheme.surfaceTint,
            ),
            // Material 主题不会把 colorError 映射到平台的 android:colorError，
            // 读平台属性会穿透到 ROM 的系统强调色，必须读 AppCompat 声明的那个
            error = view.resolveThemeColor(
                androidx.appcompat.R.attr.colorError,
                baseScheme.error,
            ),
            onError = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnError,
                baseScheme.onError,
            ),
            errorContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorErrorContainer,
                baseScheme.errorContainer,
            ),
            onErrorContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnErrorContainer,
                baseScheme.onErrorContainer,
            ),
            outline = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOutline,
                baseScheme.outline,
            ),
            outlineVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOutlineVariant,
                baseScheme.outlineVariant,
            ),
            surfaceContainer = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceContainer,
                baseScheme.surfaceContainer,
            ),
            surfaceContainerHigh = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                baseScheme.surfaceContainerHigh,
            ),
            surfaceContainerHighest = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                baseScheme.surfaceContainerHighest,
            ),
            surfaceContainerLow = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                baseScheme.surfaceContainerLow,
            ),
            surfaceContainerLowest = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceContainerLowest,
                baseScheme.surfaceContainerLowest,
            ),
        ).let { scheme ->
            scheme.copy(
                surfaceBright = deriveSurfaceBright(isDarkTheme, scheme),
                surfaceDim = deriveSurfaceDim(isDarkTheme, scheme),
            )
        }
    }
}

/**
 * 自定义配色的 XML 主题只声明了 surface 与 surfaceContainer* 色阶，没有 colorSurfaceBright / colorSurfaceDim，
 * 直接读属性会拿到 Material 基线调色板的紫色，与应用配色不符。这里按 M3 的色阶关系推导，
 * 供「读 View 主题」与「按设置项自建主题」两条路径共用，确保两者算出的卡片底色完全一致。
 */
internal fun deriveSurfaceBright(darkTheme: Boolean, scheme: ColorScheme): Color {
    return if (darkTheme) scheme.surfaceContainerHighest else scheme.surface
}

internal fun deriveSurfaceDim(darkTheme: Boolean, scheme: ColorScheme): Color {
    return if (darkTheme) scheme.surface else scheme.surfaceContainerHighest
}

private fun View.resolveThemeColor(
    attr: Int,
    fallback: Color,
): Color {
    return Color(MaterialColors.getColor(this, attr, fallback.toArgb()))
}
