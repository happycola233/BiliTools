package com.happycola233.bilitools.ui.eggs

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 从顶部两侧喷出一把彩纸，约两秒后落尽。[trigger] 每变化一次喷一次，为 0 时不喷。
 * 系统关闭动画时直接跳过。只负责绘制，不拦截触摸。
 */
@Composable
fun ConfettiBurst(
    trigger: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    var pieces by remember { mutableStateOf(emptyList<ConfettiPiece>()) }
    var elapsedNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(trigger) {
        if (trigger == 0 || !systemAnimationsEnabled()) return@LaunchedEffect
        // 同一把彩纸共用一个随机源；相邻种子各建一个会抽出几乎相同的数，彩纸挤成一团
        val random = Random(trigger)
        pieces = List(PIECE_COUNT) { index -> ConfettiPiece.random(random, colors, fromStart = index % 2 == 0) }
        val start = withFrameNanos { it }
        do {
            elapsedNanos = withFrameNanos { it } - start
        } while (elapsedNanos < DURATION_NANOS)
        pieces = emptyList()
    }
    if (pieces.isEmpty()) return
    Canvas(modifier = modifier) {
        val seconds = elapsedNanos / 1_000_000_000f
        val fade = ((DURATION_NANOS / 1e9f - seconds) / FADE_SECONDS).coerceIn(0f, 1f)
        pieces.forEach { piece ->
            val origin = Offset(if (piece.fromStart) 0f else size.width, -12f * density)
            // 抛体运动加一点空气阻力：彩纸先冲出去，再左右飘着落下
            val drag = (1f - exp(-DRAG * seconds)) / DRAG
            val displacement = Offset(
                piece.velocity.x * drag + sin(seconds * piece.flutter) * 10f,
                piece.velocity.y * drag + GRAVITY * seconds * seconds / 2f,
            )
            val center = origin + displacement * density
            rotate(piece.spin * seconds + piece.angle, pivot = center) {
                // 翻转的纸片：宽度随时间在正反面之间伸缩
                val flip = cos(seconds * piece.flutter * 1.3f)
                val pieceSize = Size(piece.width * density * flip, piece.height * density)
                drawRect(
                    color = piece.color.copy(alpha = piece.color.alpha * fade),
                    topLeft = center - Offset(pieceSize.width / 2f, pieceSize.height / 2f),
                    size = pieceSize,
                )
            }
        }
    }
}

internal data class ConfettiPiece(
    val fromStart: Boolean,
    /** dp/s */
    val velocity: Offset,
    val color: Color,
    val width: Float,
    val height: Float,
    val angle: Float,
    val spin: Float,
    val flutter: Float,
) {
    companion object {
        fun random(random: Random, colors: List<Color>, fromStart: Boolean): ConfettiPiece {
            // 从顶部两角斜向下往屏幕中间喷
            val angle = (random.nextDouble(5.0, 80.0) * PI / 180).toFloat()
            val speed = random.nextDouble(320.0, 1_100.0).toFloat()
            val direction = if (fromStart) 1f else -1f
            return ConfettiPiece(
                fromStart = fromStart,
                velocity = Offset(cos(angle) * speed * direction, sin(angle) * speed * 0.6f),
                color = colors[random.nextInt(colors.size)],
                width = random.nextDouble(6.0, 10.0).toFloat(),
                height = random.nextDouble(9.0, 14.0).toFloat(),
                angle = random.nextDouble(0.0, 360.0).toFloat(),
                spin = random.nextDouble(-360.0, 360.0).toFloat(),
                flutter = random.nextDouble(4.0, 9.0).toFloat(),
            )
        }
    }
}

private const val PIECE_COUNT = 90
private const val DURATION_NANOS = 2_600_000_000L
private const val FADE_SECONDS = 0.6f

/** dp/s² */
private const val GRAVITY = 620f
private const val DRAG = 2.0f
