package com.happycola233.bilitools.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.happycola233.bilitools.core.naming.NamingShape
import com.happycola233.bilitools.core.naming.NamingTemplateScope
import com.happycola233.bilitools.core.naming.NamingTemplateSet
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NamingSettingsScrollTest {
    @get:Rule val compose = createComposeRule()
    private var settings by mutableStateOf(AppSettings())

    @Test fun offscreenInsertionKeepsViewportInLightTheme() = verifyOffscreenInsertion(AppThemeMode.Light)

    @Test fun offscreenInsertionKeepsViewportInDarkTheme() = verifyOffscreenInsertion(AppThemeMode.Dark)

    @Test fun offscreenInsertionWithPreviewVisibleKeepsViewport() =
        verifyOffscreenInsertion(AppThemeMode.Light, keepPreviewVisible = true)

    @Test fun offscreenInsertionWithoutFocusKeepsViewport() {
        showScreen(AppThemeMode.Light)
        scrollToControls(WORK_TOKEN)
        val before = bounds(WORK_TOKEN)
        compose.onNodeWithText(WORK_TOKEN).performClick()
        compose.waitForIdle()
        assertEquals(before, bounds(WORK_TOKEN))
        assertTrue(template().endsWith("{work}"))
    }

    @Test fun replacementAndOptionalWrappingPreserveSelection() {
        showScreen(AppThemeMode.Light, "ABCDEF")
        val editor = compose.onNode(hasSetTextAction())
        editor.performScrollTo().performClick()
        editor.performSemanticsAction(SemanticsActions.SetSelection) { it(4, 2, false) }
        scrollToControls(WORK_TOKEN)
        compose.onNodeWithText(WORK_TOKEN).performClick()
        compose.waitForIdle()
        assertEquals("AB{work}EF", template())
        editor.performScrollTo().performClick()
        editor.performSemanticsAction(SemanticsActions.SetSelection) { it(2, 8, false) }
        scrollToControls(OPTIONAL)
        val before = bounds(OPTIONAL)
        compose.onNodeWithText(OPTIONAL).performClick()
        compose.waitForIdle()
        assertEquals("AB{?{work}}EF", template())
        assertEquals(before, bounds(OPTIONAL))

        editor.performScrollTo().performTextInput("X")
        compose.waitForIdle()
        assertEquals("AB{?{work}X}EF", template())
    }

    private fun verifyOffscreenInsertion(mode: AppThemeMode, keepPreviewVisible: Boolean = false) {
        showScreen(mode)
        val original = template()
        val editor = compose.onNode(hasSetTextAction())
        editor.performScrollTo().performClick()
        editor.performSemanticsAction(SemanticsActions.SetSelection) { it(original.length, original.length, false) }
        if (keepPreviewVisible) {
            scrollTo(WORK_TOKEN)
            val distance = editor.fetchSemanticsNode().boundsInRoot.bottom - bounds("命名").top
            list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
            compose.waitForIdle()
        } else {
            scrollToControls(WORK_TOKEN)
        }
        val buttonBounds = bounds(WORK_TOKEN)
        val titleBounds = bounds("命名")
        val editorBounds = editor.fetchSemanticsNode().boundsInRoot
        assertTrue("输入框应已滚出可见内容区", editorBounds.isEmpty || editorBounds.bottom <= titleBounds.bottom)

        // 连续插入使输入框与预览多次换行，并覆盖首次出现“恢复本项默认”按钮的高度变化。
        repeat(8) {
            compose.onNodeWithText(WORK_TOKEN).performClick()
            compose.waitForIdle()
            assertEquals("第 ${it + 1} 次插入后按钮移动", buttonBounds, bounds(WORK_TOKEN))
            assertEquals(titleBounds, bounds("命名"))
            editor.assertIsFocused()
        }
        assertEquals(original + "{work}".repeat(8), template())
        editor.performScrollTo().performTextInput("尾")
        compose.waitForIdle()
        assertEquals(original + "{work}".repeat(8) + "尾", template())
    }

    private fun showScreen(mode: AppThemeMode, initialTemplate: String? = null) {
        settings = AppSettings(themeMode = mode)
        initialTemplate?.let {
            settings = settings.copy(naming = settings.naming.copy(
                overrides = mapOf(NamingShape.Video to NamingTemplateSet(topFolder = it)),
            ))
        }
        compose.setContent {
            BiliToolsTheme(settings) {
                NamingSettingsScreen(
                    settings = settings,
                    onTopLevelFolderModeChange = {},
                    onOverwriteExistingFilesChange = {},
                    onCleanSeparatorsChange = {},
                    onShowSinglePageNumberChange = {},
                    onTemplateChange = { shape, scope, text ->
                        val templates = settings.naming.overrides[shape] ?: NamingTemplateSet()
                        settings = settings.copy(naming = settings.naming.copy(
                            overrides = settings.naming.overrides + (shape to templates.with(scope, text)),
                        ))
                    },
                    onTemplateReset = { _, _ -> },
                    onRestoreDefaults = {},
                    onBack = {},
                )
            }
        }
        scrollTo("顶层文件夹模板")
        compose.onNodeWithText("顶层文件夹模板").performClick()
        compose.waitForIdle()
    }

    private fun scrollTo(text: String) {
        list().performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performScrollTo()
        compose.waitForIdle()
    }

    private fun scrollToControls(text: String) {
        scrollTo(text)
        // 让变量按钮位于工具栏下方，明确覆盖输入框完全离开视口的场景。
        val distance = bounds(text).top - bounds("命名").bottom - 40f
        list().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        compose.waitForIdle()
    }

    private fun list() = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
    )

    private fun bounds(text: String): Rect = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
    private fun template() = settings.naming.template(NamingShape.Video, NamingTemplateScope.TopFolder)

    private companion object {
        const val WORK_TOKEN = "作品标题 · {work}"
        const val OPTIONAL = "⟨ 包成可选片段 ⟩"
    }
}
