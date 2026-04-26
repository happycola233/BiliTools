package com.happycola233.bilitools.ui

internal object BiliTvLaunchMotion {
    const val SQUASH_DURATION_MILLIS = 120
    const val JUMP_DURATION_MILLIS = 260
    const val SETTLE_DURATION_MILLIS = 260
    const val SPLASH_FADE_DELAY_MILLIS = 500
    const val SPLASH_FADE_DURATION_MILLIS = 260
    const val CONTENT_REVEAL_DELAY_MILLIS = 300
    const val CONTENT_REVEAL_DURATION_MILLIS = 360

    const val SPLASH_END_SCALE = 1.035f
    const val CONTENT_START_ALPHA = 0.92f
    const val CONTENT_START_OFFSET_DP = 12f

    const val ICON_SQUASH_OFFSET_DP = 7f
    const val ICON_JUMP_OFFSET_DP = -46f
    const val ICON_SQUASH_SCALE_X = 1.06f
    const val ICON_SQUASH_SCALE_Y = 0.92f
    const val ICON_JUMP_SCALE_X = 0.96f
    const val ICON_JUMP_SCALE_Y = 1.07f
    const val ICON_SQUASH_ROTATION_DEGREES = -2f
    const val ICON_JUMP_ROTATION_DEGREES = 4f
    const val SETTLE_OVERSHOOT_TENSION = 0.55f

    const val STANDARD_EASE_X1 = 0.2f
    const val STANDARD_EASE_Y1 = 0f
    const val STANDARD_EASE_X2 = 0f
    const val STANDARD_EASE_Y2 = 1f

    const val EMPHASIZED_EASE_X1 = 0.16f
    const val EMPHASIZED_EASE_Y1 = 1f
    const val EMPHASIZED_EASE_X2 = 0.3f
    const val EMPHASIZED_EASE_Y2 = 1f

    const val CONTENT_EASE_X1 = 0.05f
    const val CONTENT_EASE_Y1 = 0.7f
    const val CONTENT_EASE_X2 = 0.1f
    const val CONTENT_EASE_Y2 = 1f

    fun settleOvershoot(fraction: Float): Float {
        val shiftedFraction = fraction - 1f
        return shiftedFraction * shiftedFraction *
            ((SETTLE_OVERSHOOT_TENSION + 1f) * shiftedFraction + SETTLE_OVERSHOOT_TENSION) + 1f
    }
}
