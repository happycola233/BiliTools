package com.happycola233.bilitools.ui.eggs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.happycola233.bilitools.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 小电视各种表情与姿势的静态对照表，只用于人工查看。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MascotShowcaseScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private data class Pose(val name: String, val setup: suspend BiliTvMascotState.() -> Unit)

    private val poses = listOf(
        Pose("Open") {},
        Pose("Blink") { eyeOpenness.snapTo(0.08f) },
        Pose("Happy") { eyes = MascotEyes.Happy },
        Pose("Squint") { eyes = MascotEyes.Squint },
        Pose("Dizzy") { eyes = MascotEyes.Dizzy },
        Pose("Surprised") { eyes = MascotEyes.Surprised },
        Pose("Sleepy") { eyes = MascotEyes.Sleepy },
        Pose("Look up") { gaze.snapTo(androidx.compose.ui.geometry.Offset(0f, -0.22f)) },
        Pose("Antenna") { antennaSwing.snapTo(18f) },
        Pose("Signal") { turnOn() },
        Pose("Fainted") { eyes = MascotEyes.Dizzy; fallen.snapTo(1f) },
        Pose("Upside down") { upsideDown = true },
        Pose("Squash") { scaleX.snapTo(1.06f); scaleY.snapTo(0.92f); lift.snapTo(0.12f) },
        Pose("Mid-air") { lift.snapTo(-0.5f); scaleX.snapTo(0.96f); scaleY.snapTo(1.07f); tilt.snapTo(4f) },
    )

    @Test fun light() = capture("light", Color(0xFFF6F7FB), Color(0xFF1B1B1F))

    @Test fun dark() = capture("dark", Color(0xFF111318), Color(0xFFE3E2E6))

    private fun capture(variant: String, background: Color, onBackground: Color) {
        // 开屏后彩条与心跳会一直循环，必须在设置内容前关掉自动推进，否则永远等不到空闲
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val states = poses.map { rememberBiliTvMascotState() }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.background(background).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.bilitools_app_icon), null, Modifier.size(120.dp))
                        Text("PNG 图标", color = onBackground, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            BiliTvMascot(rememberBiliTvMascotState(), Modifier.size(120.dp * 624f / 1024f))
                        }
                        Text("代码绘制", color = onBackground, fontSize = 11.sp)
                    }
                }
                poses.chunked(5).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        chunk.forEachIndexed { column, pose ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val state = states[row * 5 + column]
                                LaunchedEffect(state) { pose.setup(state) }
                                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                    BiliTvMascot(state, Modifier.size(56.dp))
                                }
                                Text(pose.name, color = onBackground, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().saveEggShowcase("mascot/poses-$variant")
    }
}
