package com.happycola233.bilitools.ui.downloads

import com.happycola233.bilitools.data.model.DownloadEmbeddedMetadata
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class DownloadPresentationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val task = DownloadItem(
        id = 1, groupId = 1, taskType = DownloadTaskType.AudioVideo, title = "音视频", fileName = "video.mp4",
        url = "", status = DownloadStatus.Success, progress = 100, totalBytes = 10 * 1024 * 1024L,
        downloadedBytes = 10 * 1024 * 1024L, outputBytes = 8 * 1024 * 1024L,
    )
    private val group = DownloadGroup(1, "视频", null, "BV17x411w7KC", createdAt = 1, tasks = listOf(task))

    @Test fun sizesDistinguishTransferFromConvertedOutputAndExcludeAuxiliaryTasks() {
        assertEquals("8.0 MB", buildDownloadSizeText(context, task))
        assertEquals("8.0 MB", buildDownloadSizeText(context, task.copy(outputMissing = true)))
        for (status in listOf(DownloadStatus.Running, DownloadStatus.Paused, DownloadStatus.Failed)) {
            assertEquals("4.0 MB / 10.0 MB", buildDownloadSizeText(context, task.copy(status = status, downloadedBytes = 4 * 1024 * 1024L)))
        }
        assertEquals("已下载 10.0 MB", buildDownloadSizeText(context, task.copy(outputBytes = null)))
        assertEquals("大小未知", buildDownloadSizeText(context, task.copy(status = DownloadStatus.Pending, downloadedBytes = 0, totalBytes = 0)))
        for (type in DownloadTaskType.entries) {
            val size = buildDownloadSizeText(context, task.copy(taskType = type))
            assertEquals(type.name, type in setOf(DownloadTaskType.Audio, DownloadTaskType.Video, DownloadTaskType.AudioVideo), size != null)
        }
    }

    @Test fun reparsePreservesPartAndEpisodeAndWorksForAuxiliaryOnlyGroups() {
        val partUrl = "https://www.bilibili.com/video/BV17x411w7KC?p=3"
        assertEquals(partUrl, group.copy(tasks = listOf(task.copy(embeddedMetadata = DownloadEmbeddedMetadata(originalUrl = partUrl)))).sourceUrl())
        val episodeUrl = "https://www.bilibili.com/bangumi/play/ep123"
        assertEquals(episodeUrl, group.copy(
            tasks = listOf(task.copy(taskType = DownloadTaskType.Subtitle)),
            sourceMetadata = DownloadEmbeddedMetadata(originalUrl = episodeUrl),
        ).sourceUrl())
        assertEquals("https://www.bilibili.com/video/BV17x411w7KC", group.sourceUrl())
        assertEquals("https://www.bilibili.com/read/cv123", group.copy(bvid = "cv123").sourceUrl())
        assertNull(group.copy(bvid = null).sourceUrl())
    }

    @Test fun savedDetailsRoundTripAndOldRecordsKeepNullableDefaults() {
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(DownloadGroup::class.java)
        val saved = group.copy(sourceMetadata = DownloadEmbeddedMetadata(originalUrl = "https://www.bilibili.com/audio/au123", durationSeconds = 183))
        assertEquals(saved, adapter.fromJson(adapter.toJson(saved)))
        val oldJson = """{"id":1,"title":"视频","subtitle":null,"createdAt":1,"tasks":[{"id":1,"groupId":1,"taskType":"Video","title":"视频","fileName":"v.mp4","url":"","status":"Success","progress":100}]}"""
        val restored = adapter.fromJson(oldJson)!!
        assertNull(restored.sourceMetadata)
        assertNull(restored.tasks.single().outputBytes)
    }
}
