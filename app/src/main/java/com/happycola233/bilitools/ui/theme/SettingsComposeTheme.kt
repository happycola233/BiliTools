package com.happycola233.bilitools.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.overlayStyleResOrNull

/**
 * 设置页专用主题。它自己按 [AppSettings] 拼一个带配色 overlay 的 Context 再取色，
 * 所以用户在设置页里改配色能立刻看到变化，不必等 `recreate()`。
 *
 * 代价是它与 Activity 主题是两套来源，其他页面一律用 [rememberAndroidThemeColorScheme]。
 * 同一屏里混用两者会出现一半即时刷新、一半等 `recreate()` 的分批变色。
 *
 * **新增角色时这里和 `ComposeTheme.kt` 必须同步补齐**，理由见那边的说明。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiliToolsSettingsTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    /*
     * 设置页会直接按新偏好预览主题，但为了避免 Activity 重建，AppCompat 要到退出设置页时
     * 才同步。因此从“浅色”改回“跟随系统”时，LocalConfiguration 仍是应用强制的浅色配置，
     * isSystemInDarkTheme() 会误判。这里必须读取不受应用覆盖影响的设备配置。
     */
    val darkTheme = settings.themeMode.usesDarkTheme(
        deviceUiMode = Resources.getSystem().configuration.uiMode,
    )
    val colorScheme = rememberSettingsThemeColorScheme(settings, darkTheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.let { activity ->
                WindowCompat.getInsetsController(activity.window, view).run {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

internal fun AppThemeMode.usesDarkTheme(deviceUiMode: Int): Boolean {
    return when (this) {
        AppThemeMode.System ->
            (deviceUiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
}

@Composable
private fun rememberSettingsThemeColorScheme(
    settings: AppSettings,
    darkTheme: Boolean,
): ColorScheme {
    val context = LocalContext.current
    val themedContext = remember(
        context,
        settings.themeColor,
        settings.darkModePureBlack,
        darkTheme,
    ) {
        context.createSettingsThemeContext(
            themeColor = settings.themeColor,
            pureBlack = settings.darkModePureBlack,
            darkTheme = darkTheme,
        )
    }

    return remember(themedContext, darkTheme) {
        themedContext.resolveThemeColorScheme(darkTheme = darkTheme)
    }
}

private fun Context.createSettingsThemeContext(
    themeColor: AppThemeColor,
    pureBlack: Boolean,
    darkTheme: Boolean,
): Context {
    val configuration = Configuration(resources.configuration).apply {
        uiMode =
            (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val configuredContext = createConfigurationContext(configuration)
    val baseTheme = when {
        themeColor == AppThemeColor.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) {
                com.google.android.material.R.style.Theme_Material3Expressive_DynamicColors_Dark_NoActionBar
            } else {
                com.google.android.material.R.style.Theme_Material3Expressive_DynamicColors_Light_NoActionBar
            }
        }

        else -> R.style.Theme_BiliTools
    }

    return ContextThemeWrapper(configuredContext, baseTheme).apply {
        if (themeColor != AppThemeColor.Dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            themeColor.overlayStyleResOrNull()?.let { theme.applyStyle(it, true) }
        }
        if (pureBlack && darkTheme) {
            theme.applyStyle(R.style.ThemeOverlay_BiliTools_DarkPureBlack, true)
        }
    }
}

private fun Context.resolveThemeColorScheme(darkTheme: Boolean): ColorScheme {
    val baseScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val primary = resolveThemeColor(
        androidx.appcompat.R.attr.colorPrimary,
        baseScheme.primary,
    )
    val onPrimary = resolveThemeColor(
        com.google.android.material.R.attr.colorOnPrimary,
        baseScheme.onPrimary,
    )
    val primaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorPrimaryContainer,
        baseScheme.primaryContainer,
    )
    val onPrimaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorOnPrimaryContainer,
        baseScheme.onPrimaryContainer,
    )
    val secondary = resolveThemeColor(
        com.google.android.material.R.attr.colorSecondary,
        baseScheme.secondary,
    )
    val onSecondary = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSecondary,
        baseScheme.onSecondary,
    )
    val secondaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorSecondaryContainer,
        baseScheme.secondaryContainer,
    )
    val onSecondaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSecondaryContainer,
        baseScheme.onSecondaryContainer,
    )
    val tertiary = resolveThemeColor(
        com.google.android.material.R.attr.colorTertiary,
        baseScheme.tertiary,
    )
    val onTertiary = resolveThemeColor(
        com.google.android.material.R.attr.colorOnTertiary,
        baseScheme.onTertiary,
    )
    val tertiaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorTertiaryContainer,
        baseScheme.tertiaryContainer,
    )
    val onTertiaryContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorOnTertiaryContainer,
        baseScheme.onTertiaryContainer,
    )
    // fixed 色组在深浅模式下取值相同，按钮、选中胶囊这类填充面用它来保持两个模式一致
    val primaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorPrimaryFixed,
        baseScheme.primaryFixed,
    )
    val primaryFixedDim = resolveThemeColor(
        com.google.android.material.R.attr.colorPrimaryFixedDim,
        baseScheme.primaryFixedDim,
    )
    val onPrimaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorOnPrimaryFixed,
        baseScheme.onPrimaryFixed,
    )
    val onPrimaryFixedVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorOnPrimaryFixedVariant,
        baseScheme.onPrimaryFixedVariant,
    )
    val secondaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorSecondaryFixed,
        baseScheme.secondaryFixed,
    )
    val secondaryFixedDim = resolveThemeColor(
        com.google.android.material.R.attr.colorSecondaryFixedDim,
        baseScheme.secondaryFixedDim,
    )
    val onSecondaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSecondaryFixed,
        baseScheme.onSecondaryFixed,
    )
    val onSecondaryFixedVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSecondaryFixedVariant,
        baseScheme.onSecondaryFixedVariant,
    )
    val tertiaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorTertiaryFixed,
        baseScheme.tertiaryFixed,
    )
    val tertiaryFixedDim = resolveThemeColor(
        com.google.android.material.R.attr.colorTertiaryFixedDim,
        baseScheme.tertiaryFixedDim,
    )
    val onTertiaryFixed = resolveThemeColor(
        com.google.android.material.R.attr.colorOnTertiaryFixed,
        baseScheme.onTertiaryFixed,
    )
    val onTertiaryFixedVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorOnTertiaryFixedVariant,
        baseScheme.onTertiaryFixedVariant,
    )
    val background = resolveThemeColor(android.R.attr.colorBackground, baseScheme.background)
    val onBackground = resolveThemeColor(
        com.google.android.material.R.attr.colorOnBackground,
        baseScheme.onBackground,
    )
    val surface = resolveThemeColor(
        com.google.android.material.R.attr.colorSurface,
        baseScheme.surface,
    )
    val onSurface = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSurface,
        baseScheme.onSurface,
    )
    val surfaceVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceVariant,
        baseScheme.surfaceVariant,
    )
    val onSurfaceVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        baseScheme.onSurfaceVariant,
    )
    // Material 主题不会把 colorError 映射到平台的 android:colorError，
    // 读平台属性会穿透到 ROM 的系统强调色，必须读 AppCompat 声明的那个
    val error = resolveThemeColor(
        androidx.appcompat.R.attr.colorError,
        baseScheme.error,
    )
    val onError = resolveThemeColor(
        com.google.android.material.R.attr.colorOnError,
        baseScheme.onError,
    )
    val errorContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorErrorContainer,
        baseScheme.errorContainer,
    )
    val onErrorContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorOnErrorContainer,
        baseScheme.onErrorContainer,
    )
    val outline = resolveThemeColor(
        com.google.android.material.R.attr.colorOutline,
        baseScheme.outline,
    )
    val outlineVariant = resolveThemeColor(
        com.google.android.material.R.attr.colorOutlineVariant,
        baseScheme.outlineVariant,
    )
    val surfaceContainer = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceContainer,
        baseScheme.surfaceContainer,
    )
    val surfaceContainerHigh = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceContainerHigh,
        baseScheme.surfaceContainerHigh,
    )
    val surfaceContainerHighest = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceContainerHighest,
        baseScheme.surfaceContainerHighest,
    )
    val surfaceContainerLow = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceContainerLow,
        baseScheme.surfaceContainerLow,
    )
    val surfaceContainerLowest = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceContainerLowest,
        baseScheme.surfaceContainerLowest,
    )
    val surfaceBright = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceBright,
        baseScheme.surfaceBright,
    )
    val surfaceDim = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceDim,
        baseScheme.surfaceDim,
    )
    val inverseSurface = resolveThemeColor(
        com.google.android.material.R.attr.colorSurfaceInverse,
        baseScheme.inverseSurface,
    )
    val inverseOnSurface = resolveThemeColor(
        com.google.android.material.R.attr.colorOnSurfaceInverse,
        baseScheme.inverseOnSurface,
    )
    val inversePrimary = resolveThemeColor(
        com.google.android.material.R.attr.colorPrimaryInverse,
        baseScheme.inversePrimary,
    )
    return baseScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        primaryFixed = primaryFixed,
        primaryFixedDim = primaryFixedDim,
        onPrimaryFixed = onPrimaryFixed,
        onPrimaryFixedVariant = onPrimaryFixedVariant,
        secondaryFixed = secondaryFixed,
        secondaryFixedDim = secondaryFixedDim,
        onSecondaryFixed = onSecondaryFixed,
        onSecondaryFixedVariant = onSecondaryFixedVariant,
        tertiaryFixed = tertiaryFixed,
        tertiaryFixedDim = tertiaryFixedDim,
        onTertiaryFixed = onTertiaryFixed,
        onTertiaryFixedVariant = onTertiaryFixedVariant,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
    )
}

private fun Context.resolveThemeColor(
    attr: Int,
    fallback: Color,
): Color {
    val typedValue = TypedValue()
    if (!theme.resolveAttribute(attr, typedValue, true)) {
        return fallback
    }

    return when {
        typedValue.resourceId != 0 -> Color(ContextCompat.getColor(this, typedValue.resourceId))
        typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> {
            Color(typedValue.data)
        }

        else -> fallback
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
