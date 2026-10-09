package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CopyPreviewSheetTest {
    @get:Rule val compose = createComposeRule()

    private val videoTitle = "从零开始画出夏日的光影"
    private val firstPartSrt = """
        1
        00:00:00,260 --> 00:00:05,520
        先用铅笔把整体轮廓轻轻勾出来

        2
        00:00:05,520 --> 00:00:06,860
        然后铺第一层颜色

        3
        00:00:06,860 --> 00:00:08,140
        阴影部分留到最后

    """.trimIndent()
    private val subtitleEntries = listOf(
        SubtitleCopyEntry("$videoTitle - P1", "中文 · AI", firstPartSrt),
        SubtitleCopyEntry("$videoTitle - P1", "English", null, "未找到可用字幕"),
        SubtitleCopyEntry("$videoTitle - P2", "中文 · AI", "1\n00:01:02,000 --> 00:01:03,000\n第二部分\n\n"),
    )
    private val aiSummary = """
        # $videoTitle - BV17x411w7KC

        视频演示了从线稿到上色的完整流程。

        ## 线稿 - [00:00](https://www.bilibili.com/video/BV17x411w7KC?t=0)

        - 勾勒整体轮廓 - [00:12](https://www.bilibili.com/video/BV17x411w7KC?t=12)

        - 调整比例 - [01:30](https://www.bilibili.com/video/BV17x411w7KC?t=90)
    """.trimIndent()

    @Test fun lightSubtitleSheetSwitchesAndCopies() = verifySubtitleSheet(AppThemeMode.Light)
    @Test fun darkSubtitleSheetSwitchesAndCopies() = verifySubtitleSheet(AppThemeMode.Dark)
    @Test fun pureBlackSubtitleSheetSwitchesAndCopies() = verifySubtitleSheet(AppThemeMode.Dark, pureBlack = true)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp", fontScale = 1.6f)
    fun narrowLargeTextSubtitleSheetKeepsActionsReachable() = verifySubtitleSheet(AppThemeMode.Dark)

    @Test fun lightAiSummarySheetRendersMarkdownWithoutRepeatingTitle() = verifyAiSummarySheet(AppThemeMode.Light)
    @Test fun darkAiSummarySheetRendersMarkdownWithoutRepeatingTitle() = verifyAiSummarySheet(AppThemeMode.Dark)

    private fun verifySubtitleSheet(mode: AppThemeMode, pureBlack: Boolean = false) {
        val copied = mutableListOf<SubtitleCopyEntry>()
        var copiedAll: List<SubtitleCopyEntry>? = null
        var dismissed = false
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle, darkModePureBlack = pureBlack)) {
                SubtitleCopyPreviewSheet(
                    entries = subtitleEntries,
                    onDismiss = { dismissed = true },
                    onCopyCurrent = { copied += it },
                    onCopyAll = { copiedAll = it },
                )
            }
        }
        compose.onNodeWithText("2 / 3 项可复制").assertIsDisplayed()
        compose.onNodeWithText("P1 · 中文 · AI").assertIsDisplayed()
        compose.onNodeWithText("先用铅笔把整体轮廓轻轻勾出来").assertIsDisplayed()
        compose.onNodeWithText("00:05").assertIsDisplayed()
        capture("subtitle-$mode-$pureBlack")

        compose.onNodeWithText("P1 · English").performClick()
        compose.onNodeWithText("未找到可用字幕").assertIsDisplayed()
        capture("subtitle-unavailable-$mode-$pureBlack")

        compose.onNodeWithText("P2 · 中文 · AI").performClick()
        compose.onNodeWithText("01:02").assertIsDisplayed()
        compose.onNodeWithContentDescription("复制当前条目").performClick()
        compose.runOnIdle { assertEquals(listOf(subtitleEntries[2]), copied) }

        compose.onNodeWithText("复制全部").performClick()
        compose.runOnIdle {
            assertEquals(subtitleEntries, copiedAll)
            assertTrue("复制全部后收起面板", dismissed)
        }
    }

    private fun verifyAiSummarySheet(mode: AppThemeMode) {
        val entries = listOf(
            AiSummaryCopyEntry("$videoTitle - P1", aiSummary),
            AiSummaryCopyEntry("$videoTitle - P2", null, "该视频暂无 AI 总结"),
        )
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Periwinkle)) {
                AiSummaryCopyPreviewSheet(entries = entries, onDismiss = {}, onCopyCurrent = {}, onCopyAll = {})
            }
        }
        compose.onNodeWithText("1 / 2 项可复制").assertIsDisplayed()
        compose.onNodeWithText("P1").assertIsDisplayed()
        compose.waitUntil { compose.onAllNodesWithText("视频演示了从线稿到上色的完整流程。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("$videoTitle - BV17x411w7KC").assertDoesNotExist()
        compose.onNodeWithText("线稿 - 00:00").assertIsDisplayed()
        capture("ai-summary-$mode")
    }

    @Test
    fun pullingPreviewPastTopKeepsSheetOpenWhileHeaderStillDismisses() {
        var dismissed = false
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = AppThemeMode.Light, themeColor = AppThemeColor.Periwinkle)) {
                SubtitleCopyPreviewSheet(
                    entries = subtitleEntries,
                    onDismiss = { dismissed = true },
                    onCopyCurrent = {},
                    onCopyAll = {},
                )
            }
        }
        val title = compose.onNodeWithText("复制字幕")
        val sheetTop = title.getUnclippedBoundsInRoot().top
        val firstCue = compose.onNodeWithText("先用铅笔把整体轮廓轻轻勾出来")
        // 慢拖与快速甩动都从已在顶部的字幕列表开始，剩余位移和惯性不能把面板带下去。
        firstCue.performTouchInput { swipe(center, center + Offset(0f, 900f), durationMillis = 600) }
        compose.runOnIdle { assertFalse("预览滚到顶后继续下拉不应关闭面板", dismissed) }
        firstCue.performTouchInput { swipe(center, center + Offset(0f, 900f), durationMillis = 80) }
        compose.runOnIdle { assertFalse("预览滚到顶后快速甩动不应关闭面板", dismissed) }
        assertEquals("面板停在原位", sheetTop, title.getUnclippedBoundsInRoot().top)

        title.performTouchInput { swipe(center, center + Offset(0f, 1500f), durationMillis = 200) }
        compose.runOnIdle { assertTrue("拖动面板顶部仍可关闭", dismissed) }
    }

    @Test
    fun tabLabelsKeepOnlyTheDistinguishingTitleSegments() {
        assertEquals(
            listOf("P1", "P1", "P2"),
            distinctTitleParts(listOf("作品 - 上篇 - P1", "作品 - 上篇 - P1", "作品 - 上篇 - P2")),
        )
        assertEquals(listOf(null, null), distinctTitleParts(listOf("同一个视频", "同一个视频")))
        assertEquals(listOf("第一集", "第二集"), distinctTitleParts(listOf("第一集", "第二集")))
        assertEquals(listOf(null, "P2"), distinctTitleParts(listOf("作品", "作品 - P2")))
        assertEquals(
            listOf("夏日写生", "冬日速写"),
            distinctTitleParts(listOf("夏日写生 - 默认收藏夹", "冬日速写 - 默认收藏夹")),
        )
    }

    @Test
    fun subtitleCuesKeepEmptyAndMultilineText() {
        val srt = "1\n00:00:01,500 --> 00:00:02,000\n第一行\n第二行\n\n" +
            "2\n01:02:03,040 --> 01:02:04,000\n\n\n" +
            "3\n01:02:05,000 --> 01:02:06,000\n结束\n\n"
        assertEquals(
            listOf(
                SubtitleCue(1_500, "第一行\n第二行"),
                SubtitleCue(3_723_040, ""),
                SubtitleCue(3_725_000, "结束"),
            ),
            parseSubtitleCues(srt),
        )
        assertEquals("00:01", formatCueTime(1_500, withHours = false))
        assertEquals("0:00:01", formatCueTime(1_500, withHours = true))
        assertEquals("1:02:03", formatCueTime(3_723_040, withHours = true))
    }

    private fun capture(name: String) {
        val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        File("../.tmp/copy-preview/$name-$width.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
