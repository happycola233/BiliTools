package com.happycola233.bilitools.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelFolderModeTest {
    @Test fun autoLeavesASingleItemUnwrapped() {
        assertFalse(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("BV1xx - 标题")))
        assertFalse(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("")))
    }

    @Test fun autoWrapsItemsThatLandInDifferentFolders() {
        assertTrue(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("BV1aa - 甲", "BV1bb - 乙")))
    }

    @Test fun autoSkipsTheExtraLayerWhenTheBatchAlreadySharesOneFolder() {
        assertFalse(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("某番剧", "某番剧", "某番剧")))
    }

    @Test fun autoWrapsABatchWhoseItemFolderTemplateIsEmpty() {
        // 项目文件夹为空时，整批文件会直接落进下载根目录。
        assertTrue(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("", "")))
    }

    @Test fun autoWrapsABatchWhereOnlySomeItemsHaveAFolder() {
        assertTrue(TopLevelFolderMode.Auto.createsTopLevelFolder(listOf("合集", "")))
    }

    @Test fun explicitModesIgnoreTheBatchShape() {
        assertTrue(TopLevelFolderMode.Enabled.createsTopLevelFolder(listOf("BV1xx - 标题")))
        assertFalse(TopLevelFolderMode.Disabled.createsTopLevelFolder(listOf("", "")))
    }
}
