package com.happycola233.bilitools.ui.liquidtabs

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.happycola233.bilitools.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidBottomTabsTest {
    @get:Rule val compose = createComposeRule()
    private var selectedIndex by mutableIntStateOf(0)
    private val selections = mutableListOf<Int>()

    @Test fun lightContainerPreviewsFromAnyCellAndKeepsTheCellAnchor() = verifyContainer(dark = false)
    @Test fun darkContainerPreviewsFromAnyCellAndKeepsTheCellAnchor() = verifyContainer(dark = true)
    @Test fun narrowRtlContainerMirrorsTheInputAndKeepsTheCellAnchor() =
        verifyContainer(dark = true, rtl = true, width = 188)

    @Test fun lightQuickTapCommitsBeforeTheGlassFeedbackFinishes() = verifyQuickTap(dark = false)
    @Test fun darkQuickTapCommitsBeforeTheGlassFeedbackFinishes() = verifyQuickTap(dark = true)

    @Test fun rapidTapsCommitImmediatelyAndAccessibilityClicksStillWork() {
        showTabs()
        compose.mainClock.autoAdvance = false
        tabs().performTouchInput {
            down(Offset(298f, 32f))
            up()
            down(Offset(14f, 32f))
            up()
            down(Offset(114f, 32f))
            up()
        }
        compose.runOnIdle {
            assertEquals(listOf(2, 0, 1), selections)
            assertEquals(1, selectedIndex)
        }
        compose.onNodeWithText("我的").performClick()
        compose.runOnIdle { assertEquals(listOf(2, 0, 1, 2), selections) }
    }

    @Test fun cancelledPreviewDoesNotCommitAndNextGestureCanStartImmediately() {
        showTabs()
        tabs().performTouchInput {
            down(Offset(150f, 32f))
            moveTo(Offset(270f, 32f))
            cancel()
        }
        compose.runOnIdle {
            assertTrue(selections.isEmpty())
            assertEquals(0, selectedIndex)
        }
        tabs().performTouchInput {
            down(Offset(150f, 32f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(1), selections) }
    }

    @Test fun paddingBelongsToTheContainerAndStationaryHoldOnlyCommitsOnRelease() {
        showTabs()
        compose.mainClock.autoAdvance = false
        tabs().performTouchInput { down(Offset(306f, 1f)) }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        tabs().performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2), selections) }
    }

    @Test fun cancellationRestoresTheLatestExternalPageWithoutWritingBackThePreview() {
        showTabs()
        compose.mainClock.autoAdvance = false
        tabs().performTouchInput { down(Offset(150f, 32f)) }
        compose.runOnIdle { selectedIndex = 2 }
        compose.mainClock.advanceTimeBy(200)
        tabs().performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle {
            assertEquals(2, selectedIndex)
            assertTrue(selections.isEmpty())
        }
        capture("cancel-external-page")
    }

    private fun verifyQuickTap(dark: Boolean) {
        showTabs(dark = dark)
        compose.mainClock.autoAdvance = false
        tabs().performTouchInput { down(Offset(150f, 32f)) }
        compose.mainClock.advanceTimeBy(32)
        tabs().performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(1), selections) }
        compose.mainClock.advanceTimeBy(160)
        val theme = if (dark) "dark" else "light"
        capture("$theme-tap-peak")
        compose.mainClock.advanceTimeBy(800)
        capture("$theme-tap-settled")
    }

    private fun verifyContainer(dark: Boolean, rtl: Boolean = false, width: Int = 308) {
        showTabs(dark, rtl, width)
        val name = "${if (dark) "dark" else "light"}-${if (rtl) "rtl" else "ltr"}"
        capture("$name-rest")
        compose.mainClock.autoAdvance = false
        val tabWidth = (width - 8f) / 3f
        val direction = if (rtl) -1f else 1f
        // 从未选中菜单靠近边缘的位置按下，再越过格子边界：仍应按格子中心 + 增量判定落点。
        val startX = width / 2f + direction * tabWidth * 0.44f
        tabs().performTouchInput { down(Offset(startX, 32f)) }
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        capture("$name-pressed")
        tabs().performTouchInput { moveTo(Offset(startX + direction * tabWidth * 0.25f, 32f)) }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnIdle { assertTrue(selections.isEmpty()) }
        tabs().performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(1), selections) }

        // 在前一次收尾尚未结束时重新抓住，继续拖到边缘并保持按压。
        tabs().performTouchInput { down(Offset(width / 2f, 32f)) }
        repeat(10) { step ->
            tabs().performTouchInput {
                moveTo(Offset(width / 2f + direction * tabWidth * (step + 1) / 5f, 32f))
            }
            compose.mainClock.advanceTimeBy(16)
        }
        capture("$name-edge")
        compose.runOnIdle { assertEquals(listOf(1), selections) }
        tabs().performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(1, 2), selections) }
        compose.mainClock.advanceTimeBy(1_000)
        capture("$name-settled")
    }

    private fun showTabs(dark: Boolean = false, rtl: Boolean = false, width: Int = 308) {
        compose.setContent {
            val attachInfo = ReflectionHelpers.getField<Any>(LocalView.current, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mHardwareAccelerated", true)
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(380.dp, 200.dp).testTag("scene")) {
                        Column(
                            Modifier.fillMaxSize().layerBackdrop(backdrop)
                                .background(MaterialTheme.colorScheme.surface).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            repeat(5) { Text("正在下载　已完成　视频与音频", color = MaterialTheme.colorScheme.onSurface) }
                        }
                        LiquidBottomTabs(
                            selectedTabIndex = { selectedIndex },
                            onTabSelected = ::selectTab,
                            backdrop = backdrop,
                            tabsCount = 3,
                            accentColor = MaterialTheme.colorScheme.primary,
                            containerColor = (if (dark) Color.Black else Color.White).copy(alpha = 0.1f),
                            glassStyle = LiquidGlassStyle(8f, 16f, 0.5f, true),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                                .width(width.dp).testTag("tabs"),
                        ) {
                            listOf("解析", "下载", "我的").forEachIndexed { index, label ->
                                LiquidBottomTab(onClick = { selectTab(index) }) {
                                    val icon = when (index) {
                                        0 -> R.drawable.ic_home_24
                                        1 -> R.drawable.ic_download_for_offline_24
                                        else -> R.drawable.ic_account_circle_24
                                    }
                                    Icon(
                                        painterResource(icon), contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun selectTab(index: Int) {
        selectedIndex = index
        selections += index
    }

    private fun tabs() = compose.onNodeWithTag("tabs")

    private fun capture(name: String) {
        val file = File("../.tmp/liquid-tabs/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            compose.onNodeWithTag("scene").captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
