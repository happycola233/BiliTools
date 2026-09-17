package com.happycola233.bilitools.ui.parse

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.happycola233.bilitools.data.AppSettings
import com.happycola233.bilitools.data.AppThemeMode
import com.happycola233.bilitools.data.model.StreamFormat
import com.happycola233.bilitools.ui.theme.BiliToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamFormatControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unavailableFormatIsDisabledInLightTheme() = verify(AppThemeMode.Light)
    @Test fun unavailableFormatIsDisabledInDarkTheme() = verify(AppThemeMode.Dark)

    @Test fun temporaryDisabledControlsDoNotShowUnsupportedReason() {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ConnectedFormatButtons(
                    selected = StreamFormat.Dash,
                    enabled = false,
                    unavailableFormats = setOf(StreamFormat.Flv),
                    onFormatChange = { error("Disabled controls must not change the format") },
                )
            }
        }
        compose.onNodeWithText("FLV 格式").assertIsNotEnabled().performTouchInput { longClick() }
        compose.onNodeWithText("当前内容不提供 FLV 格式。").assertDoesNotExist()
    }

    @Test fun batchUnsupportedReasonOnlyAppearsOnLongPress() {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ConnectedFormatButtons(
                    selected = StreamFormat.Dash,
                    enabled = true,
                    unavailableFormats = setOf(StreamFormat.Flv),
                    isMultiSelect = true,
                    onFormatChange = { error("Long pressing an unsupported format must not select it") },
                )
            }
        }
        val reason = "部分所选条目不提供 FLV 格式。"
        compose.onNodeWithText(reason).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("FLV 格式").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText(reason).assertIsDisplayed()
    }

    @Test fun noCommonFormatKeepsActionableMessageVisible() {
        compose.setContent {
            BiliToolsTheme(AppSettings()) {
                ConnectedFormatButtons(
                    selected = StreamFormat.Dash,
                    enabled = true,
                    unavailableFormats = StreamFormat.entries.toSet(),
                    isMultiSelect = true,
                    onFormatChange = { error("Unavailable formats must not be selected") },
                )
            }
        }
        compose.onNodeWithText("所选条目没有共同的流媒体格式，请分开下载。").assertIsDisplayed()
        compose.onNodeWithText("DASH 格式").assertIsNotEnabled()
        compose.onNodeWithText("MP4 格式").assertIsNotEnabled()
        compose.onNodeWithText("FLV 格式").assertIsNotEnabled()
    }

    private fun verify(theme: AppThemeMode) {
        var chosen: StreamFormat? = null
        compose.setContent {
            BiliToolsTheme(AppSettings(themeMode = theme)) {
                ConnectedFormatButtons(
                    selected = StreamFormat.Dash,
                    enabled = true,
                    unavailableFormats = setOf(StreamFormat.Flv),
                    onFormatChange = { chosen = it },
                )
            }
        }
        val reason = "当前内容不提供 FLV 格式。"
        compose.onNodeWithText(reason).assertDoesNotExist()
        compose.onNodeWithText("FLV 格式").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(null, chosen) }
        compose.onNodeWithText("DASH 格式").assertIsEnabled()
        assertEquals(
            compose.onNodeWithText("DASH 格式").fetchSemanticsNode().boundsInRoot.width,
            compose.onNodeWithText("FLV 格式").fetchSemanticsNode().boundsInRoot.width,
            1f,
        )
        compose.onNodeWithText("MP4 格式").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(StreamFormat.Mp4, chosen) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("FLV 格式").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithText(reason).assertIsDisplayed()
        compose.onNodeWithText("FLV 格式").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(StreamFormat.Mp4, chosen) }
    }
}
