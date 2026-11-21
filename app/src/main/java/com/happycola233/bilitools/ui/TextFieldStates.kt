package com.happycola233.bilitools.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** 只接受数字：粘贴时混入的其他字符会被剔除，而不是整次输入被拒绝。 */
internal fun digitsOnlyInput(maxLength: Int = Int.MAX_VALUE): InputTransformation =
    InputTransformation.byValue { _, proposed -> proposed.filter(Char::isDigit).take(maxLength) }

internal val DigitsOnlyInput: InputTransformation = digitsOnlyInput()

/**
 * 为外部（如 ViewModel）持有的文本创建输入框状态：用户编辑经 [onValueChange] 即时上报，
 * [value] 的外部改动（粘贴按钮、外部链接、重置等）写回输入框。
 *
 * 上报值经 StateFlow 回到界面时可能落后几次输入，若拿它直接与输入框比较并写回，
 * 会把刚输入的字符覆盖掉；因此只把「不是本输入框上报过」的值当作外部改动。
 */
@Composable
internal fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val sync = remember { ExternalTextSync() }
    val state = remember {
        TextFieldState(initialText = value, onTextChanged = { text ->
            if (!sync.writingExternalValue) {
                val reported = text.toString()
                sync.unconfirmedReports.addLast(reported)
                latestOnValueChange(reported)
            }
        })
    }
    LaunchedEffect(value) {
        val echoIndex = sync.unconfirmedReports.indexOf(value)
        if (echoIndex >= 0) {
            repeat(echoIndex + 1) { sync.unconfirmedReports.removeFirst() }
        } else if (state.text.toString() != value) {
            sync.unconfirmedReports.clear()
            sync.writingExternalValue = true
            state.setTextAndPlaceCursorAtEnd(value)
            sync.writingExternalValue = false
        }
    }
    return state
}

private class ExternalTextSync {
    /** 已上报、尚未经外部值回传确认的文本，按上报顺序排列。 */
    val unconfirmedReports = ArrayDeque<String>()

    /** 正在把外部值写回输入框；此时的文本变化不是用户输入，不再上报。 */
    var writingExternalValue = false
}
