package com.happycola233.bilitools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/** 复制文本并始终提供应用内反馈，避免依赖不同 ROM 的系统剪贴板提示。 */
internal fun Context.copyTextWithFeedback(
    content: String,
    @StringRes clipLabelRes: Int,
    @StringRes feedbackRes: Int,
) {
    copyTextToClipboard(label = getString(clipLabelRes), content = content)
    Toast.makeText(this, getString(feedbackRes), Toast.LENGTH_SHORT).show()
}

internal fun Context.copyTextToClipboard(label: String, content: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, content))
}
