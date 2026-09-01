package com.happycola233.bilitools.ui

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.happycola233.bilitools.ui.theme.AppSurfaces
import com.happycola233.bilitools.ui.theme.usesDarkSurfaces
import com.happycola233.bilitools.ui.theme.usesPureBlackSurfaces

/** 全应用模态容器的视觉默认值，保证普通深色与纯黑模式都能与底层内容拉开层次。 */
internal object AppDialogDefaults {
    val containerColor: Color
        @Composable
        get() = AppSurfaces.modalContainerColor

    val outlineColor: Color
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            when {
                usesPureBlackSurfaces() -> outlineVariant.copy(alpha = 0.50f)
                usesDarkSurfaces() -> outlineVariant.copy(alpha = 0.30f)
                else -> Color.Transparent
            }
        }

    val scrimAlpha: Float
        @Composable
        get() = with(MaterialTheme.colorScheme) {
            when {
                usesPureBlackSurfaces() -> 0.48f
                usesDarkSurfaces() -> 0.42f
                else -> 0.32f
            }
        }
}

/** Material [AlertDialog] 的应用级入口，浅色沿用默认观感，深色统一提升模态层级。 */
@Composable
internal fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val shape = AlertDialogDefaults.shape
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfigureAppDialogWindow()
            confirmButton()
        },
        modifier = modifier.appDialogBorder(shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = AppDialogDefaults.containerColor,
        properties = properties,
    )
}

/** 日期选择器与普通对话框共用同一套模态表面、边缘和背景遮罩。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDatePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = DatePickerDefaults.shape
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfigureAppDialogWindow()
            confirmButton()
        },
        modifier = modifier.appDialogBorder(shape),
        dismissButton = dismissButton,
        shape = shape,
        colors = DatePickerDefaults.colors(containerColor = AppDialogDefaults.containerColor),
        properties = properties,
        content = content,
    )
}

/** 保留 Compose [Dialog] 的自定义内容能力，只统一其窗口遮罩。 */
@Composable
internal fun AppDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        ConfigureAppDialogWindow()
        content()
    }
}

/** 自定义对话框容器使用此修饰符，标准对话框已由 [AppAlertDialog] 自动应用。 */
@Composable
internal fun Modifier.appDialogBorder(shape: Shape): Modifier {
    val outlineColor = AppDialogDefaults.outlineColor
    return if (outlineColor == Color.Transparent) {
        this
    } else {
        border(BorderStroke(1.dp, outlineColor), shape)
    }
}

/**
 * Compose Dialog 使用独立平台窗口，默认遮罩不会随 Compose 内的纯黑设置重组。
 * 在对话框内直接同步当前语义值，才能让设置页的即时主题预览也正确生效。
 */
@Composable
private fun ConfigureAppDialogWindow() {
    val scrimAlpha = AppDialogDefaults.scrimAlpha
    val window = (LocalView.current.parent as DialogWindowProvider).window
    SideEffect {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(scrimAlpha)
    }
}
