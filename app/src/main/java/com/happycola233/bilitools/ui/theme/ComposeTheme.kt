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
            primary = view.resolveThemeColor(android.R.attr.colorPrimary, baseScheme.primary),
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
            surfaceTint = view.resolveThemeColor(android.R.attr.colorPrimary, baseScheme.surfaceTint),
            error = view.resolveThemeColor(android.R.attr.colorError, baseScheme.error),
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
        )
    }
}

private fun View.resolveThemeColor(
    attr: Int,
    fallback: Color,
): Color {
    return Color(MaterialColors.getColor(this, attr, fallback.toArgb()))
}
