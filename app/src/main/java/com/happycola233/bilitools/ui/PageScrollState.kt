package com.happycola233.bilitools.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/** 末页不足一屏时，只为显式跳页补足必要的底部空间，使目标页首仍能对齐顶部。 */
@Stable
internal class PageScrollState {
    var bottomPaddingPx: Int by mutableIntStateOf(0)
        private set

    suspend fun scrollToPage(listState: LazyListState, firstItemIndex: Int) {
        listState.scrollToItem(firstItemIndex)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstItemIndex } ?: return
        if (target.offset <= 0) return
        val requiredPadding = listState.layoutInfo.afterContentPadding + target.offset
        bottomPaddingPx += target.offset
        // 等待新增 padding 参与布局，随后才能突破原本的列表底部边界。
        snapshotFlow { listState.layoutInfo.afterContentPadding }.first { it >= requiredPadding }
        listState.scrollToItem(firstItemIndex)
    }
}
