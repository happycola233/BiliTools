package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeColor
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.MediaContributor
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.assertEquals
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
class ParseMetadataDetailsSheetTest {
    @get:Rule val compose = createComposeRule()
    private val contentId = "BV17x411w7KC"
    private val previewUrl = "android.resource://com.happycola233.bilitools/${R.drawable.default_avatar}"
    private val metadata = ParseMetadataDisplay(
        subjectKey = contentId,
        publicIdText = contentId, publicIdCopyValue = contentId, publicIdCopyName = "BV 号",
        summarySlots = emptyList(),
        sections = listOf(
            ParseMetadataSection.Values("基本信息", listOf(
                ParseMetadataRow("内容编号", contentId),
                ParseMetadataRow("发布时间", "2026-09-01"),
                ParseMetadataRow("时长", "10:25"),
            )),
            ParseMetadataSection.Groups("分 P 信息", listOf(
                ParseMetadataGroup(
                    title = "P1", subtitle = "从零开始画出夏日的光影：线稿与色彩的练习", previewUrl = previewUrl,
                    rows = listOf(
                        ParseMetadataRow("分辨率", "1920 × 1080"),
                        ParseMetadataRow("清晰度", "1080P 高清"),
                        ParseMetadataRow("视频编码", "AVC (H.264)", note = "当前选择的视频流"),
                    ),
                ),
            )),
            ParseMetadataSection.Contributors("合作成员", listOf(
                MediaContributor("绘画记录", 123L, role = "制作"),
                MediaContributor("声音记录", 0L, role = "配音"),
            )),
        ),
    )

    @Test fun lightDetailsCopyOnLongPressAndKeepContributorNavigation() = verifyDetails(AppThemeMode.Light)
    @Test fun darkDetailsCopyOnLongPressAndKeepContributorNavigation() = verifyDetails(AppThemeMode.Dark)

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h720dp", fontScale = 1.6f)
    fun narrowDetailsWithLargeTextRemainScrollable() = verifyDetails(AppThemeMode.Dark)

    private fun verifyDetails(mode: AppThemeMode) {
        val copied = mutableListOf<String>()
        val opened = mutableListOf<Long>()
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = mode, themeColor = AppThemeColor.Sakura)) {
                MetadataDetailsBottomSheet(
                    metadata = metadata, onDismiss = {}, onOpenUpper = { opened += it },
                    onCopyUpperName = { copied += it }, onCopyValue = { copied += it },
                )
            }
        }
        val value = compose.onNodeWithText(contentId).performScrollTo()
        val valueBounds = value.getUnclippedBoundsInRoot()
        val labelBounds = compose.onNodeWithText("内容编号").getUnclippedBoundsInRoot()
        val nextBounds = compose.onNodeWithText("2026-09-01").getUnclippedBoundsInRoot()
        assertEquals("保留原有行距", 12f, (nextBounds.top - valueBounds.bottom).value, 0.5f)
        assertEquals("保留原有列距", 20f, (valueBounds.left - labelBounds.right).value, 0.5f)
        assertEquals("字段与取值顶部对齐", labelBounds.top.value, valueBounds.top.value, 0.5f)
        value.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        value.performTouchInput { click() }
        compose.runOnIdle { assertTrue(copied.isEmpty()) }
        val sheet = compose.onNode(isDialog())
        val beforePress = sheet.captureToImage().asAndroidBitmap()
        val valuePixels = value.fetchSemanticsNode().boundsInRoot
        val feedbackGap = with(compose.density) { 2.dp.toPx() }
        val feedbackX = valuePixels.center.x.toInt()
        val feedbackY = (valuePixels.top - feedbackGap).toInt()
        compose.mainClock.autoAdvance = false
        value.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        val image = sheet.captureToImage().asAndroidBitmap()
        assertTrue("反馈略超出文字边缘", beforePress.getPixel(feedbackX, feedbackY) != image.getPixel(feedbackX, feedbackY))
        assertEquals("扩大反馈不改变文字布局", valueBounds, value.getUnclippedBoundsInRoot())
        val width = RuntimeEnvironment.getApplication().resources.configuration.screenWidthDp
        File("../.tmp/downloads-ui/parse-details-pressed-${mode.name}-$width.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        value.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        value.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf(contentId), copied); copied.clear() }

        val previewDescription = RuntimeEnvironment.getApplication().getString(R.string.parse_metadata_part_preview_desc)
        compose.onNodeWithContentDescription(previewDescription).performScrollTo().performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf(previewUrl), copied); copied.clear() }
        compose.onNodeWithText("绘画记录").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(123L), opened); assertTrue(copied.isEmpty()) }
        compose.onNodeWithText("绘画记录").performTouchInput { longClick() }
        compose.onNodeWithText("声音记录").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("配音").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.runOnIdle {
            assertEquals(listOf("绘画记录", "声音记录", "配音"), copied)
            assertEquals("长按昵称不打开主页", listOf(123L), opened)
            copied.clear()
        }
        value.performScrollTo().assertIsDisplayed()
        value.performTouchInput { swipeUp(durationMillis = 200) }
        compose.runOnIdle { assertTrue("滑动浏览不复制", copied.isEmpty()) }
    }
}
