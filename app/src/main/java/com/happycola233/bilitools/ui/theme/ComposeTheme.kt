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

/**
 * 把 Activity 当前的 View 主题（基础主题 + 配色 overlay + 纯黑 overlay）翻译成 Compose 的
 * [ColorScheme]，让同一屏里的 View 控件与 Compose 内容取到完全一致的颜色。
 *
 * 因为读的是 Activity 主题，切换配色需要 `recreate()` 才会生效。设置页用的是
 * [BiliToolsSettingsTheme]，它由 `AppSettings` 驱动、改完即时刷新，两者不要混用：
 * 同一屏里一半即时刷新、一半等 `recreate()`，会看到明显的分批变色。
 *
 * **新增角色时这里和 `SettingsComposeTheme.kt` 必须同步补齐。** 没覆盖的角色会静默停在
 * `lightColorScheme()` / `darkColorScheme()` 的基线紫，当下没组件用到就发现不了
 * （`inverseSurface` 一族就这样漏了很久），等哪天加个 Snackbar 才会突然蹦出来。
 */
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
            // fixed 色组在深浅模式下取值相同，按钮、选中胶囊这类填充面用它来保持两个模式一致
            primaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorPrimaryFixed,
                baseScheme.primaryFixed,
            ),
            primaryFixedDim = view.resolveThemeColor(
                com.google.android.material.R.attr.colorPrimaryFixedDim,
                baseScheme.primaryFixedDim,
            ),
            onPrimaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnPrimaryFixed,
                baseScheme.onPrimaryFixed,
            ),
            onPrimaryFixedVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnPrimaryFixedVariant,
                baseScheme.onPrimaryFixedVariant,
            ),
            secondaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSecondaryFixed,
                baseScheme.secondaryFixed,
            ),
            secondaryFixedDim = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSecondaryFixedDim,
                baseScheme.secondaryFixedDim,
            ),
            onSecondaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSecondaryFixed,
                baseScheme.onSecondaryFixed,
            ),
            onSecondaryFixedVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSecondaryFixedVariant,
                baseScheme.onSecondaryFixedVariant,
            ),
            tertiaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorTertiaryFixed,
                baseScheme.tertiaryFixed,
            ),
            tertiaryFixedDim = view.resolveThemeColor(
                com.google.android.material.R.attr.colorTertiaryFixedDim,
                baseScheme.tertiaryFixedDim,
            ),
            onTertiaryFixed = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnTertiaryFixed,
                baseScheme.onTertiaryFixed,
            ),
            onTertiaryFixedVariant = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnTertiaryFixedVariant,
                baseScheme.onTertiaryFixedVariant,
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
            inverseSurface = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceInverse,
                baseScheme.inverseSurface,
            ),
            inverseOnSurface = view.resolveThemeColor(
                com.google.android.material.R.attr.colorOnSurfaceInverse,
                baseScheme.inverseOnSurface,
            ),
            inversePrimary = view.resolveThemeColor(
                com.google.android.material.R.attr.colorPrimaryInverse,
                baseScheme.inversePrimary,
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
            surfaceBright = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceBright,
                baseScheme.surfaceBright,
            ),
            surfaceDim = view.resolveThemeColor(
                com.google.android.material.R.attr.colorSurfaceDim,
                baseScheme.surfaceDim,
            ),
        )
    }
}

private fun View.resolveThemeColor(
    attr: Int,
    fallback: Color,
): Color {
    return Color(MaterialColors.getColor(this, attr, fallback.toArgb()))
}
