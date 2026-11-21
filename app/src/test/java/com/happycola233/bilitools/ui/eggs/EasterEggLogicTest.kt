package com.happycola233.bilitools.ui.eggs

import android.os.LocaleList
import com.happycola233.bilitools.data.EasterEggCopy
import com.happycola233.bilitools.data.EasterEggRepository
import com.happycola233.bilitools.data.EggCopyLanguage
import com.happycola233.bilitools.data.EggMilestone
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.data.model.MediaInfo
import com.happycola233.bilitools.data.model.MediaItem
import com.happycola233.bilitools.data.model.MediaNfo
import com.happycola233.bilitools.data.model.MediaType
import com.happycola233.bilitools.data.model.MediaUpper
import com.happycola233.bilitools.ui.downloads.isBatchJustCompleted
import com.happycola233.bilitools.ui.settings.DeveloperOptionsTapCounter
import com.happycola233.bilitools.ui.settings.DeveloperTapOutcome
import java.time.LocalDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EasterEggLogicTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val repository by lazy { EasterEggRepository(context) }
    private val copy by lazy { repository.copy(EggCopyLanguage.SimplifiedChinese) }
    private val afternoon = LocalDateTime.of(2026, 11, 21, 15, 0)

    @Test
    fun everyCopyFileParsesWithCompleteMilestonesAndPokes() {
        EggCopyLanguage.entries.forEach { language ->
            val parsed = repository.copy(language)
            assertTrue("$language tips", parsed.tips.isNotEmpty())
            assertTrue("$language late night", parsed.lateNightTips.isNotEmpty())
            assertTrue("$language april fools", parsed.aprilFoolsTips.isNotEmpty())
            assertEquals("$language launch", EasterEggRepository.LAUNCH_MILESTONES, parsed.launchMilestones.keys)
            assertEquals("$language downloads", EasterEggRepository.DOWNLOAD_MILESTONES, parsed.downloadMilestones.keys)
            assertEquals("$language pokes", setOf(5, 8, 12, 15), parsed.mascot.pokes.keys)
            assertEquals("$language dates", setOf("06-26", "10-24"), parsed.dateTips.keys)
            assertEquals("$language followers", setOf(1_000L, 10_000L, 100_000L, 1_000_000L), parsed.profile.followers.keys)
            assertNull(parsed.profile.followerLine(999))
            assertEquals(parsed.profile.followers.getValue(1_000), parsed.profile.followerLine(3_812))
            assertEquals(parsed.profile.followers.getValue(1_000_000), parsed.profile.followerLine(23_000_000))
            // 带数字的文案必须能正常格式化
            parsed.appBirthday.formatEggCopy(1)
            parsed.result.vintage.formatEggCopy(12)
            parsed.profile.levelOther.formatEggCopy(3)
        }
    }

    @Test
    fun copyLanguageFollowsResourceSelection() {
        fun resolve(tags: String) = EggCopyLanguage.resolve(LocaleList.forLanguageTags(tags))
        assertEquals(EggCopyLanguage.SimplifiedChinese, resolve("zh-CN"))
        assertEquals(EggCopyLanguage.TraditionalChinese, resolve("zh-TW"))
        assertEquals(EggCopyLanguage.TraditionalChinese, resolve("zh-HK"))
        assertEquals(EggCopyLanguage.English, resolve("en-US"))
        // 应用支持日语，但日语没有彩蛋词库
        assertNull(resolve("ja-JP"))
        // 系统语言都不支持时界面回退到简体中文，彩蛋文案跟着回退
        assertEquals(EggCopyLanguage.SimplifiedChinese, resolve("de-DE"))
        assertEquals(EggCopyLanguage.English, resolve("de-DE,en-GB"))
    }

    @Test
    fun openingTipPrefersMilestoneThenDateThenLateNight() {
        val picker = HomeTipPicker(copy, Random(1))
        assertEquals(
            HomeTip(copy.launchMilestones.getValue(233), HomeTipMood.Celebrate),
            picker.opening(afternoon, EggMilestone.Launch(233)),
        )
        assertEquals(copy.dateTips.getValue("06-26"), picker.opening(LocalDateTime.of(2027, 6, 26, 2, 0), null).text)
        assertEquals("今天是 BiliTools 的 2 岁生日，谢谢你一直陪着它！", picker.opening(LocalDateTime.of(2028, 2, 4, 9, 0), null).text)
        // 发布当年的 2 月 4 日还不满一岁，不祝生日
        assertEquals(HomeTipMood.Normal, picker.opening(LocalDateTime.of(2026, 2, 4, 9, 0), null).mood)
        val lateNight = picker.opening(LocalDateTime.of(2026, 11, 21, 3, 0), null)
        assertTrue(lateNight.text in copy.lateNightTips)
        assertEquals(HomeTipMood.Sleepy, lateNight.mood)
        assertEquals(HomeTipMood.Fools, picker.opening(LocalDateTime.of(2027, 4, 1, 3, 0), null).mood)
    }

    @Test
    fun nextTipNeverRepeatsAndCanRickroll() {
        val picker = HomeTipPicker(copy, Random(42))
        var current = picker.next(afternoon, null)
        var rickrolled = false
        repeat(500) {
            val next = picker.next(afternoon, current)
            assertFalse(next.text == current.text)
            if (next.parseTarget != null) {
                assertEquals(EasterEggCopy.RICKROLL_TARGET, next.parseTarget)
                rickrolled = true
            }
            current = next
        }
        assertTrue(rickrolled)
    }

    @Test
    fun onlyLateNightLinesLookSleepy() {
        val picker = HomeTipPicker(copy, Random(9))
        val night = LocalDateTime.of(2026, 11, 22, 2, 0)
        var current: HomeTip? = null
        val picks = List(400) { picker.next(night, current).also { current = it } }
        picks.forEach { tip ->
            assertEquals(tip.text in copy.lateNightTips, tip.mood == HomeTipMood.Sleepy)
        }
        val sleepyShare = picks.count { it.mood == HomeTipMood.Sleepy } / picks.size.toFloat()
        assertTrue(sleepyShare in 0.3f..0.5f)
        // 白天不会说困话
        assertTrue((0 until 200).none { picker.next(afternoon, null).mood == HomeTipMood.Sleepy })
    }

    @Test
    fun parseEggsReactOnlyToCheersAndFifthEmptyParse() {
        val controller = HomeTipController(copy, HomeTipPicker(copy, Random(1)), { afternoon }, null)
        assertTrue(controller.interceptParse(" 干杯 "))
        assertEquals(HomeTipMood.Cheers, controller.tip.mood)
        assertTrue(controller.interceptParse("Cheers"))
        assertFalse(controller.interceptParse("BV1GJ411x7h7"))
        repeat(HomeTipController.AIR_PARSE_STREAK - 1) { assertFalse(controller.interceptParse("")) }
        assertEquals(HomeTipMood.Cheers, controller.tip.mood)
        // 空输入仍交给正常流程报错，只是小电视接一句
        assertFalse(controller.interceptParse("  "))
        assertEquals(copy.parseAir, controller.tip.text)
        // 中间夹了一次正常解析，连击重新计数
        repeat(2) { controller.interceptParse("") }
        controller.interceptParse("av2")
        repeat(HomeTipController.AIR_PARSE_STREAK - 1) { controller.interceptParse("") }
        assertEquals(copy.parseAir, controller.tip.text)
        assertEquals(HomeTipMood.Normal, HomeTipController(copy, HomeTipPicker(copy, Random(1)), { afternoon }, null).tip.mood)
    }

    @Test
    fun resultEggsFollowPriority() {
        val now = 1_800_000_000L
        fun detect(mid: Long = 7, aid: Long = 123, pubTime: Long = now - 86_400) =
            ResultEgg.detect(info(item(mid, aid, pubTime)), item(mid, aid, pubTime), now)
        assertEquals(ResultEgg.Developer, detect(mid = ResultEgg.DEVELOPER_MID, pubTime = 1_300_000_000L))
        assertEquals(ResultEgg.Treasure, detect(aid = ResultEgg.TREASURE_AID))
        assertEquals(ResultEgg.FirstUpload, detect(mid = ResultEgg.FOUNDER_MID, aid = ResultEgg.FIRST_UPLOAD_AID))
        assertEquals(ResultEgg.Founder, detect(mid = ResultEgg.FOUNDER_MID))
        assertEquals(ResultEgg.Vintage(15), detect(pubTime = now - (15.5 * 365.2425 * 86_400).toLong()))
        assertNull(detect(pubTime = now - (9.9 * 365.2425 * 86_400).toLong()))
        assertEquals(ResultEgg.Fresh, detect(pubTime = now - 600))
        assertNull(detect(pubTime = now - 7_200))
        assertEquals("这个视频 15 岁了 · 爷青回", ResultEgg.Vintage(15).text(copy.result))
    }

    @Test
    fun milestonesAreCongratulatedOnce() {
        repeat(232) { repository.recordAppLaunch() }
        assertEquals(EggMilestone.Launch(100), repository.takePendingMilestone())
        assertNull(repository.takePendingMilestone())
        repository.recordAppLaunch()
        assertEquals(EggMilestone.Launch(233), repository.takePendingMilestone())

        repository.recordMediaDownloaded(10L)
        assertEquals(EggMilestone.Downloads(1), repository.takePendingMilestone())
        repository.recordMediaDownloaded(EasterEggRepository.TERABYTE)
        assertEquals(EggMilestone.Terabyte, repository.takePendingMilestone())
        repository.recordMediaDownloaded(EasterEggRepository.TERABYTE)
        assertNull(repository.takePendingMilestone())
    }

    @Test
    fun versionTapsUnlockDeveloperOptionsLikeAndroid() {
        val counter = DeveloperOptionsTapCounter()
        val outcomes = (0 until 7).map { counter.tap(now = it * 300L, alreadyEnabled = false) }
        assertEquals(
            listOf(
                DeveloperTapOutcome.None,
                DeveloperTapOutcome.None,
                DeveloperTapOutcome.StepsAway(4),
                DeveloperTapOutcome.StepsAway(3),
                DeveloperTapOutcome.StepsAway(2),
                DeveloperTapOutcome.StepsAway(1),
                DeveloperTapOutcome.Unlocked,
            ),
            outcomes,
        )
        assertEquals(DeveloperTapOutcome.AlreadyEnabled, counter.tap(now = 5_000, alreadyEnabled = true))
        // 停顿太久重新计数
        val slow = DeveloperOptionsTapCounter()
        repeat(3) { slow.tap(now = it * 300L, alreadyEnabled = false) }
        assertEquals(DeveloperTapOutcome.None, slow.tap(now = 10_000, alreadyEnabled = false))
    }

    @Test
    fun batchCompletionNeedsEveryWorkingTaskToSucceed() {
        val running = group(task(1, DownloadStatus.Running), task(2, DownloadStatus.Pending))
        assertTrue(isBatchJustCompleted(listOf(running), listOf(group(task(1, DownloadStatus.Success), task(2, DownloadStatus.Success)))))
        assertFalse(isBatchJustCompleted(listOf(running), listOf(group(task(1, DownloadStatus.Success), task(2, DownloadStatus.Failed)))))
        assertFalse(isBatchJustCompleted(listOf(running), listOf(group(task(1, DownloadStatus.Success), task(2, DownloadStatus.Running)))))
        assertFalse(isBatchJustCompleted(listOf(running), listOf(group(task(1, DownloadStatus.Success), task(2, DownloadStatus.Paused)))))
        // 已经全部完成的列表再刷新一次，不重复庆祝
        val done = group(task(1, DownloadStatus.Success))
        assertFalse(isBatchJustCompleted(listOf(done), listOf(done)))
    }

    private fun item(mid: Long, aid: Long, pubTime: Long) = MediaItem(
        title = "t",
        coverUrl = "",
        description = "",
        url = "",
        duration = 1,
        pubTime = pubTime,
        type = MediaType.Video,
        upper = MediaUpper(name = "u", mid = mid),
        isTarget = true,
        index = 0,
        aid = aid,
    )

    private fun info(item: MediaItem) = MediaInfo(MediaType.Video, "id", MediaNfo(), listOf(item))

    private fun task(id: Long, status: DownloadStatus) = DownloadItem(
        id = id,
        groupId = 1,
        taskType = DownloadTaskType.AudioVideo,
        title = "t",
        fileName = "f",
        url = "",
        status = status,
        progress = 0,
    )

    private fun group(vararg tasks: DownloadItem) = DownloadGroup(
        id = 1,
        title = "g",
        subtitle = null,
        createdAt = 0,
        tasks = tasks.toList(),
    )
}
