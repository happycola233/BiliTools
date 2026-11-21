package com.happycola233.bilitools.ui.downloads

import com.happycola233.bilitools.data.model.DownloadGroup

enum class DownloadSectionType {
    /**
     * 按加入先后排列。下载队列从最早加入的任务开始执行，正在下载的组因此在最上方；
     * 新加入的组排到末尾，不会把正在下载的组挤出视野。
     */
    Downloading,

    /** 最近下载的在最上方。 */
    Downloaded,
}

/** 加入下载的先后顺序；同一毫秒内创建的组再按递增的组 ID 区分。 */
private val addedOrder = compareBy<DownloadGroup>({ it.createdAt }, { it.id })

/** 按分区约定排列组，见 [DownloadSectionType] 各取值的说明。 */
internal fun DownloadSectionType.arrange(groups: List<DownloadGroup>): List<DownloadGroup> = when (this) {
    DownloadSectionType.Downloading -> groups.sortedWith(addedOrder)
    DownloadSectionType.Downloaded -> groups.sortedWith(addedOrder.reversed())
}
