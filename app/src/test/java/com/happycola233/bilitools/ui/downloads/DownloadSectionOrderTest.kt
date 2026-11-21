package com.happycola233.bilitools.ui.downloads

import com.happycola233.bilitools.data.model.DownloadGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSectionOrderTest {
    private fun group(id: Long, createdAt: Long) =
        DownloadGroup(id = id, title = "组 $id", subtitle = null, createdAt = createdAt, tasks = emptyList())

    // 仓库按创建时间倒序给出；同一毫秒内创建的 2、3 号组顺序由组 ID 决定。
    private val groups = listOf(group(4, 300), group(3, 200), group(2, 200), group(1, 100))

    @Test fun downloadingFollowsQueueOrderSoRunningGroupsStayOnTop() {
        assertEquals(listOf(1L, 2L, 3L, 4L), DownloadSectionType.Downloading.arrange(groups).map { it.id })
    }

    @Test fun downloadedShowsMostRecentFirst() {
        assertEquals(listOf(4L, 3L, 2L, 1L), DownloadSectionType.Downloaded.arrange(groups.reversed()).map { it.id })
    }
}
