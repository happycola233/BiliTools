package com.happycola233.bilitools.ui.settings

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32, 35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLanguageChangeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun pickerChangesTextAndDirectionWithoutReplacingTheActivityOrItsContent() {
        withSettingsActivity { controller ->
            val activity = controller.get()
            val content = activity.contentView()
            val viewModel = activity.settingsViewModel()
            openLanguageSettings(activity)

            selectLanguage(controller, AppLanguage.English, throughPicker = true)
            assertSameActivityAndContent(controller, activity, content)
            assertSame(viewModel, activity.settingsViewModel())
            compose.onNodeWithText("Language").assertIsDisplayed()
            compose.onNodeWithText("English").assertIsSelected()
            assertBackButtonDirection(activity, rtl = false)

            selectLanguage(controller, AppLanguage.Arabic, throughPicker = true)
            assertSameActivityAndContent(controller, activity, content)
            compose.onNodeWithText(localizedString(activity, "ar", R.string.settings_language_title))
                .assertIsDisplayed()
            compose.onNodeWithText(AppLanguage.Arabic.displayName(activity)).assertIsSelected()
            assertBackButtonDirection(activity, rtl = true)
            assertEquals(
                listOf(SettingsDestination.Main, SettingsDestination.General, SettingsDestination.Language),
                viewModel.backStack.toList(),
            )

            // 跟随系统也会改变配置，不能只验证两种显式选择的语言。
            selectLanguage(controller, AppLanguage.System, throughPicker = true)
            assertSameActivityAndContent(controller, activity, content)
            compose.onNodeWithText(localizedString(activity, "zh-CN", R.string.settings_language_title))
                .assertIsDisplayed()
            compose.onNodeWithText(localizedString(activity, "zh-CN", R.string.settings_language_system))
                .assertIsSelected()
            assertBackButtonDirection(activity, rtl = false)
            compose.onNodeWithContentDescription(activity.getString(R.string.settings_back)).performClick()
            compose.runOnIdle { assertEquals(SettingsDestination.General, viewModel.backStack.last()) }
        }
    }

    @Test
    fun configurationChangesKeepTheLanguageListScrollPosition() {
        withSettingsActivity { controller ->
            val activity = controller.get()
            val content = activity.contentView()
            openLanguageSettings(activity)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bahasa Indonesia"))
            val originalScrollPosition = languageListScrollPosition()
            assertTrue("语言列表必须先滚动，才能验证位置保留", originalScrollPosition > 0f)

            selectLanguage(controller, AppLanguage.English)
            assertSameActivityAndContent(controller, activity, content)
            assertEquals(originalScrollPosition, languageListScrollPosition(), 0.01f)
            compose.onNodeWithText("Bahasa Indonesia").assertIsDisplayed()

            selectLanguage(controller, AppLanguage.Arabic)
            assertSameActivityAndContent(controller, activity, content)
            assertEquals(originalScrollPosition, languageListScrollPosition(), 0.01f)
            compose.onNodeWithText("Bahasa Indonesia").assertIsDisplayed()
            assertBackButtonDirection(activity, rtl = true)
        }
    }

    @Test
    fun aboutSummariesRefreshWhenTheExistingActivityReceivesANewLanguage() {
        withSettingsActivity { controller ->
            val activity = controller.get()
            val content = activity.contentView()
            compose.onNodeWithText(activity.getString(R.string.settings_about_title)).performClick()
            val oldLogStatus = activity.getString(R.string.settings_issue_report_status_disabled)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(oldLogStatus, substring = true))
            compose.onNodeWithText(oldLogStatus, substring = true).assertIsDisplayed()

            selectLanguage(controller, AppLanguage.English)
            assertSameActivityAndContent(controller, activity, content)
            assertEquals(SettingsDestination.About, activity.settingsViewModel().backStack.last())
            val newLogStatus = localizedString(activity, "en", R.string.settings_issue_report_status_disabled)
            compose.onNodeWithText(newLogStatus, substring = true).assertIsDisplayed()
            compose.onNodeWithText(oldLogStatus, substring = true).assertDoesNotExist()
            val updateSummary = localizedString(activity, "en", R.string.settings_check_update_desc)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(updateSummary, substring = true))
            compose.onNodeWithText(updateSummary, substring = true).assertIsDisplayed()
        }
    }

    private fun withSettingsActivity(block: (ActivityController<SettingsActivity>) -> Unit) {
        // AppCompat 对 "robolectric" 指纹跳过 Activity 配置 Context 的隔离，导致
        // 测试中的应用语言覆盖系统语言；使用正常设备路径才能验证“跟随系统”。
        ShadowBuild.setFingerprint("bilitools-locale-regression-test")
        Robolectric.buildActivity(SettingsActivity::class.java).setup().visible().use { controller ->
            compose.waitForIdle()
            block(controller)
        }
    }

    private fun openLanguageSettings(activity: SettingsActivity) {
        compose.onNodeWithText(activity.getString(R.string.settings_general_title)).performClick()
        compose.onNodeWithText(activity.getString(R.string.settings_language_title)).performClick()
        compose.waitForIdle()
    }

    private fun selectLanguage(
        controller: ActivityController<SettingsActivity>,
        language: AppLanguage,
        throughPicker: Boolean = false,
    ) {
        if (throughPicker) {
            val label = language.displayName(controller.get())
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performClick()
        } else {
            compose.runOnIdle { AppLanguage.select(language) }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            // Robolectric 的 LocaleManager 只保存选择，不派发系统配置变化；通过
            // ActivityController 模拟系统分发，仍由 manifest 决定是否重建 Activity。
            // API 32 则完全走实际 AppCompat 回调，不手动分发配置来掩盖刷新问题。
            compose.runOnIdle {
                val configuration = Configuration(controller.get().resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language.languageTag.ifEmpty { "zh-CN" }))
                }
                controller.configurationChange(configuration)
            }
        }
        compose.waitForIdle()
    }

    private fun assertSameActivityAndContent(
        controller: ActivityController<SettingsActivity>,
        originalActivity: SettingsActivity,
        originalContent: View,
    ) {
        compose.runOnIdle {
            assertSame(originalActivity, controller.get())
            assertFalse(originalActivity.isDestroyed)
            assertSame(originalContent, controller.get().contentView())
        }
    }

    private fun assertBackButtonDirection(activity: SettingsActivity, rtl: Boolean) {
        assertEquals(
            if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
            activity.resources.configuration.layoutDirection,
        )
        val backBounds = compose.onNodeWithContentDescription(activity.getString(R.string.settings_back))
            .getUnclippedBoundsInRoot()
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        assertEquals(
            "返回按钮必须随 Compose 布局方向移动",
            rtl,
            backBounds.left + backBounds.right > rootBounds.left + rootBounds.right,
        )
    }

    private fun languageListScrollPosition(): Float = compose.onNode(hasScrollAction())
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun SettingsActivity.contentView(): View = findViewById<ViewGroup>(android.R.id.content).getChildAt(0)

    private fun SettingsActivity.settingsViewModel(): SettingsViewModel =
        ViewModelProvider(this)[SettingsViewModel::class.java]

    private fun localizedString(activity: SettingsActivity, tag: String, resourceId: Int): String {
        val configuration = Configuration(activity.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return activity.createConfigurationContext(configuration).getString(resourceId)
    }
}
