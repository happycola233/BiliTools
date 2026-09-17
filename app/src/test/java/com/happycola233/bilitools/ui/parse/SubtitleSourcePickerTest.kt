package com.happycola233.bilitools.ui.parse

import android.graphics.Bitmap
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.SubtitleInfo
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.launch

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w320dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubtitleSourcePickerTest {
    @get:Rule val compose = createComposeRule()
    private val chinese = SubtitleInfo("zh-Hans", "中文（简体）", "https://example.com/zh")
    private val generatedEnglish = SubtitleInfo("en-US", "英语（美国）", "https://example.com/en", isAi = true)
    private val ready = ParseUiState(
        isLoggedIn = true,
        subtitleList = listOf(chinese, generatedEnglish),
        subtitleKnownLanguages = listOf(chinese, generatedEnglish),
        subtitleLoadStatus = SubtitleLoadStatus.Ready,
        subtitleAvailableCounts = mapOf("zh-Hans" to 3, "en-US" to 2),
        subtitleTargetCount = 3, subtitleCompletedCount = 3,
    )

    @Test fun lightAtNormalFontSize() = verifyPresentation(AppThemeMode.Light, 1f)
    @Test fun darkAtNormalFontSize() = verifyPresentation(AppThemeMode.Dark, 1f)
    @Test fun lightAtLargeFontSize() = verifyPresentation(AppThemeMode.Light, 1.6f)
    @Test fun darkAtLargeFontSize() = verifyPresentation(AppThemeMode.Dark, 1.6f)

    private fun verifyPresentation(theme: AppThemeMode, fontScale: Float) {
        setContent(theme, fontScale) {
            SubtitleSourcePicker(state = ready, selection = SubtitleLanguageSelection.All,
                enabled = true, onSelectionChange = {}, onRetry = {})
        }
        compose.onNodeWithText("全部字幕 · 2 种").assertIsDisplayed()
        // 全选按各条目自身可用字幕处理，目录不同不应显示缺失警告。
        compose.onNodeWithText("部分条目没有所选字幕。").assertDoesNotExist()
        compose.onNodeWithText("英语（美国） · AI 字幕").assertDoesNotExist()
        capture("summary-${theme.name.lowercase()}-$fontScale", false)
        open()
        val aiRow = dialogText("英语（美国）").assertIsOn()
        aiRow.assert(hasText("AI 字幕"))
        dialogText("中文（简体）").assert(hasText("AI 字幕").not())
        assertEquals(1, aiRow.fetchSemanticsNode().config[SemanticsProperties.Text].count { it.text == "英语（美国）" })
        aiBadge().assertHasNoClickAction()
        dialogText("2 / 3 个条目可用").assertIsDisplayed()
        dialogText("部分条目没有所选字幕。").assertDoesNotExist()
        dialogText("包含 AI 字幕").assertDoesNotExist()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("英语（美国）") and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        capture("sources-${theme.name.lowercase()}-$fontScale", true)
        val layout = layouts.single()
        // 紧随文字排布时，Text 的缓存段落宽度可大于自身尺寸；检查实际行宽而非缓存宽度。
        assertFalse("Source label must fit vertically", layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertFalse("Source label must not be truncated", layout.isLineEllipsized(line))
            assertTrue("Source label must fit horizontally", layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width)
        }
    }

    @Test fun selectAllAlwaysReflectsActualChoicesAndOnlyConfirmCommits() {
        var selection by mutableStateOf<SubtitleLanguageSelection>(SubtitleLanguageSelection.All)
        setContent {
            SubtitleSourcePicker(state = ready, selection = selection, enabled = true,
                onSelectionChange = { selection = it }, onRetry = {})
        }
        open()
        dialogText("全选").assertIsOn()
        // 标签只有说明语义，真实点按它仍由整行处理，不产生第二个选择控件。
        aiBadge().assertHasNoClickAction().performTouchInput { click() }
        dialogText("英语（美国）").assertIsOff()
        dialogText("全选").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.All, selection) }
        dialogText("取消").performClick()
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.All, selection) }
        open()
        dialogText("英语（美国）").assertIsOn()
        dialogText("全选").performClick().assertIsOff()
        dialogText("中文（简体）").assertIsOff()
        dialogText("英语（美国）").assertIsOff()
        dialogText("全选").performClick().assertIsOn()
        dialogText("英语（美国）").assertIsOn()
        dialogText("中文（简体）").performClick()
        dialogText("完成").performClick()
        compose.runOnIdle { assertEquals(SubtitleLanguageSelection.Languages(setOf("en-US")), selection) }
    }

    @Test fun allAiCatalogHasNoContradictoryFilterAndCanBeCleared() {
        var selection by mutableStateOf<SubtitleLanguageSelection>(SubtitleLanguageSelection.All)
        val aiOnly = ready.copy(subtitleList = listOf(generatedEnglish))
        setContent {
            SubtitleSourcePicker(state = aiOnly, selection = selection, enabled = true,
                onSelectionChange = { selection = it }, onRetry = {})
        }
        open()
        dialogText("全选").assertIsOn()
        dialogText("英语（美国）").assertIsOn()
        dialogText("全选").performClick()
        dialogText("完成").performClick()
        compose.runOnIdle { assertTrue(selection.isEmpty) }
        compose.onNodeWithText("请至少选择一种字幕。").assertIsDisplayed()
        compose.onNodeWithText("选择语言").assertIsDisplayed()
    }

    @Test fun failedLookupKeepsSelectionAndAllowsRetry() {
        var state by mutableStateOf(ready.copy(subtitleLoadStatus = SubtitleLoadStatus.Failed, subtitleFailedCount = 1))
        var retries = 0
        setContent {
            SubtitleSourcePicker(state = state, selection = SubtitleLanguageSelection.Languages(setOf("zh-Hans")), enabled = true,
                onSelectionChange = {}, onRetry = { retries++; state = ready })
        }
        compose.onNodeWithText("1 个条目的字幕加载失败。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        open()
        dialogText("中文（简体）").assertIsOn()
        dialogText("英语（美国）").assertIsOff()
    }

    @Test fun unavailableSelectedLanguageCanBeRemovedWithoutRowsJumping() {
        var selection by mutableStateOf<SubtitleLanguageSelection>(SubtitleLanguageSelection.Languages(setOf("zh-Hans")))
        val state = ready.copy(subtitleList = listOf(generatedEnglish), subtitleAvailableCounts = mapOf("en-US" to 3))
        setContent {
            SubtitleSourcePicker(state = state, selection = selection, enabled = true,
                onSelectionChange = { selection = it }, onRetry = {})
        }
        open()
        dialogText("中文（简体）").assertIsOn()
        dialogText("0 / 3 个条目可用").assertIsDisplayed()
        dialogText("中文（简体）").performClick().assertIsOff()
        dialogText("完成").performClick()
        compose.runOnIdle { assertTrue(selection.isEmpty) }
    }

    @Test fun lyricsUseOneRadioSelectionAndCommitOnlyOnConfirmation() {
        var selected by mutableStateOf<String?>(null)
        setContent(AppThemeMode.Dark, 1.6f) {
            SubtitleSourcePicker(state = ready, selection = SubtitleLanguageSelection.All, enabled = true,
                onSelectionChange = {}, onRetry = {}, singleSelection = true,
                selectedLanguage = selected, onLanguageSelected = { selected = it })
        }
        open("歌词语言")
        dialogText("全选").assertDoesNotExist()
        dialogText("完成").assertIsNotEnabled()
        dialogText("英语（美国）").performClick().assertIsSelected()
        compose.runOnIdle { assertNull(selected) }
        capture("lyrics-dark-1.6", true)
        dialogText("完成").performClick()
        compose.runOnIdle { assertEquals("en-US", selected) }
    }

    @Test fun unsignedEmptyCatalogExplainsLogin() {
        var state by mutableStateOf(ready.copy(isLoggedIn = false, subtitleList = emptyList()))
        setContent {
            SubtitleSourcePicker(state = state, selection = SubtitleLanguageSelection.All, enabled = true,
                onSelectionChange = {}, onRetry = {})
        }
        compose.onNodeWithText("登录后可获取视频字幕。").assertIsDisplayed()
        compose.onNodeWithText("这些条目暂无可用字幕。").assertDoesNotExist()
        state = state.copy(isLoggedIn = true)
        compose.onNodeWithText("这些条目暂无可用字幕。").assertIsDisplayed()
    }

    @Test fun missingPreviouslyChosenLyricsDoNotIncorrectlyRequireTurningTheFeatureOff() {
        setContent {
            SubtitleSourcePicker(state = ready.copy(subtitleList = emptyList()), selection = SubtitleLanguageSelection.All,
                enabled = true, onSelectionChange = {}, onRetry = {}, singleSelection = true, selectedLanguage = "zh-Hans")
        }
        compose.onNodeWithText("部分条目没有所选字幕。").assertIsDisplayed()
        compose.onNodeWithText("暂无可用字幕，请关闭内嵌歌词后下载。").assertDoesNotExist()
        open("歌词语言")
        dialogText("中文（简体）").assertIsSelected()
        dialogText("完成").assertIsEnabled()
    }

    @Test fun longAiListScrollsWhileConfirmationRemainsVisible() {
        val sources = listOf("中文", "English", "日本語", "Español", "العربية", "Português", "Français", "한국어")
            .mapIndexed { index, name -> SubtitleInfo("ai-$index", name, "https://example.com/$index", isAi = true) }
        setContent(AppThemeMode.Light, 1.6f) {
            SubtitleSourcePicker(state = ready.copy(subtitleList = sources, subtitleAvailableCounts = sources.associate { it.lan to 1 }),
                selection = SubtitleLanguageSelection.All, enabled = true, onSelectionChange = {}, onRetry = {})
        }
        open()
        dialogText("完成").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText("한국어"))
        dialogText("한국어").assertIsDisplayed().assertIsOn()
        dialogText("完成").assertIsDisplayed()
        capture("many-languages-light-1.6", true)
    }

    @Test fun pressedInkStaysInsideRoundedRow() {
        setContent {
            // 用确定的满幅状态层检验裁剪边界，避免依赖主机不能稳定截图的原生 RenderThread 涟漪。
            CompositionLocalProvider(LocalIndication provides TestPressIndication) {
                SubtitleSourcePicker(state = ready, selection = SubtitleLanguageSelection.All, enabled = true,
                    onSelectionChange = {}, onRetry = {})
            }
        }
        open()
        val row = dialogText("英语（美国）")
        val before = row.captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        val pressed = row.captureToImage().toPixelMap()
        assertEquals("Top-left corner must stay outside the ripple", before[1, 1], pressed[1, 1])
        assertEquals("Bottom-right corner must stay outside the ripple", before[before.width - 2, before.height - 2], pressed[pressed.width - 2, pressed.height - 2])
        assertTrue("Press must produce visible feedback", (0 until before.width).any { x -> before[x, before.height / 2] != pressed[x, pressed.height / 2] })
        capture("pressed-rounded-row", true)
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    private fun open(label: String = "字幕语言") = compose.onNodeWithText(label).performClick()
    private fun dialogText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))
    private fun aiBadge() = compose.onNode(hasText("AI 字幕") and hasAnyAncestor(isDialog()), useUnmergedTree = true)

    private fun setContent(theme: AppThemeMode = AppThemeMode.Light, fontScale: Float = 1f, content: @Composable () -> Unit) {
        // Dialog 有独立的 Android 窗口；仅覆盖父 Composition 的 LocalDensity 无法测试其字体缩放。
        RuntimeEnvironment.setFontScale(fontScale)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BiliToolsTheme(AppSettings(themeMode = theme)) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
                    }
                }
            }
        }
    }

    private fun capture(name: String, dialog: Boolean) {
        val file = File("../.tmp/subtitle-redesign/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            (if (dialog) compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)) else compose.onRoot()).captureToImage()
                .asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private data object TestPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressIndicationNode(interactionSource)
}

private class PressIndicationNode(private val interactions: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private var pressed by mutableStateOf(false)
    override fun onAttach() {
        coroutineScope.launch {
            interactions.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                }
            }
        }
    }
    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(Color.Black.copy(alpha = 0.12f))
    }
}
