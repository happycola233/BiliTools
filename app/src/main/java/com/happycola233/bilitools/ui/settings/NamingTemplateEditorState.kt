package com.happycola233.bilitools.ui.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.text.TextRange
import com.happycola233.bilitools.core.naming.NamingRenderer
import com.happycola233.bilitools.core.naming.NamingShape
import com.happycola233.bilitools.core.naming.NamingTemplateScope
import com.happycola233.bilitools.core.naming.NamingToken

/** 编辑区与变量区共用状态，编辑区离开 LazyColumn 的组合后仍保留光标和选区。 */
internal class NamingTemplateEditorState(initialText: String) {
    val textFieldState = TextFieldState(initialText)
    var coordinates: LayoutCoordinates? = null
    var viewportBounds: () -> Rect = { Rect.Zero }
    var suppressRelocation = false

    private val isVisible: Boolean
        get() = coordinates?.takeIf { it.isAttached }?.boundsInWindow()?.let {
            !it.isEmpty && it.overlaps(viewportBounds())
        } == true

    val keepViewport: Boolean
        get() = suppressRelocation && !isVisible

    fun prepareInsertion(listState: LazyListState, controlsKey: String) {
        suppressRelocation = !isVisible
        if (!suppressRelocation) return

        // 以变量区为锚点；上方输入框、预览或“恢复默认”按钮增高时也不挤动当前内容。
        listState.layoutInfo.visibleItemsInfo.first { it.key == controlsKey }.let {
            listState.requestScrollToItem(it.index, -it.offset)
        }
    }

    fun insertToken(token: NamingToken) {
        textFieldState.edit {
            val start = selection.min
            val insertion = "{${token.key}}"
            replace(start, selection.max, insertion)
            selection = TextRange(start + insertion.length)
        }
    }

    fun wrapSelectionAsOptional() {
        textFieldState.edit {
            val start = selection.min
            val selected = asCharSequence().subSequence(start, selection.max).toString()
            replace(start, selection.max, NamingRenderer.wrapAsOptional(selected))
            // 空选区停在 {?|} 中，非空选区停在被包裹内容末尾。
            selection = TextRange(start + 2 + selected.length)
        }
    }
}

@Composable
internal fun rememberNamingTemplateEditorState(
    shape: NamingShape,
    scope: NamingTemplateScope,
    value: String,
    onValueChange: (String) -> Unit,
): NamingTemplateEditorState {
    val state = remember(shape, scope) { NamingTemplateEditorState(value) }
    val currentValue = rememberUpdatedState(value)
    val currentOnValueChange = rememberUpdatedState(onValueChange)
    LaunchedEffect(state, value) {
        if (value != state.textFieldState.text.toString()) {
            state.textFieldState.edit {
                val cursor = selection.start.coerceAtMost(value.length)
                replace(0, length, value)
                selection = TextRange(cursor)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.textFieldState.text.toString() }.collect {
            // 外部恢复默认只同步编辑器，不再回写成自定义模板。
            if (it != currentValue.value) currentOnValueChange.value(it)
        }
    }
    return state
}

internal fun Modifier.namingEditorRelocation(state: NamingTemplateEditorState): Modifier =
    then(NamingEditorRelocationElement(state))

private data class NamingEditorRelocationElement(val state: NamingTemplateEditorState) :
    ModifierNodeElement<NamingEditorRelocationNode>() {
    override fun create() = NamingEditorRelocationNode(state)
    override fun update(node: NamingEditorRelocationNode) {
        node.state = state
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "namingEditorRelocation"
    }
}

private class NamingEditorRelocationNode(var state: NamingTemplateEditorState) :
    Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        // 按钮插入仍更新光标与输入法，但不把屏幕外的光标重新滚入视口。
        if (state.keepViewport) return
        bringIntoView {
            if (!childCoordinates.isAttached) return@bringIntoView null
            val bounds = boundsProvider() ?: return@bringIntoView null
            val offset = requireLayoutCoordinates()
                .localBoundingBoxOf(childCoordinates, clipBounds = false).topLeft
            bounds.translate(offset)
        }
    }
}
