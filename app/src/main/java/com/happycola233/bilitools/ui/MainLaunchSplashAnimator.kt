package com.happycola233.bilitools.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.doOnDetach

internal object MainLaunchSplashAnimator {
    private val standardEase = PathInterpolator(
        BiliTvLaunchMotion.STANDARD_EASE_X1,
        BiliTvLaunchMotion.STANDARD_EASE_Y1,
        BiliTvLaunchMotion.STANDARD_EASE_X2,
        BiliTvLaunchMotion.STANDARD_EASE_Y2,
    )
    private val emphasizedEase = PathInterpolator(
        BiliTvLaunchMotion.EMPHASIZED_EASE_X1,
        BiliTvLaunchMotion.EMPHASIZED_EASE_Y1,
        BiliTvLaunchMotion.EMPHASIZED_EASE_X2,
        BiliTvLaunchMotion.EMPHASIZED_EASE_Y2,
    )
    private val contentEase = PathInterpolator(
        BiliTvLaunchMotion.CONTENT_EASE_X1,
        BiliTvLaunchMotion.CONTENT_EASE_Y1,
        BiliTvLaunchMotion.CONTENT_EASE_X2,
        BiliTvLaunchMotion.CONTENT_EASE_Y2,
    )
    private val settleEase = OvershootInterpolator(BiliTvLaunchMotion.SETTLE_OVERSHOOT_TENSION)

    fun play(
        splashScreenView: SplashScreenViewProvider,
        contentView: View?,
    ) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            splashScreenView.remove()
            return
        }

        val splashView = splashScreenView.view
        val iconView = splashScreenView.iconView
        var removed = false

        fun removeSplash() {
            if (removed) return
            removed = true
            contentView?.alpha = 1f
            contentView?.translationY = 0f
            if (splashView.parent != null) {
                splashScreenView.remove()
            }
            splashView.setLayerType(View.LAYER_TYPE_NONE, null)
            iconView.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        contentView?.prepareForReveal()
        splashView.prepareForExitScale()
        iconView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val iconHop = AnimatorSet().apply {
            playSequentially(
                iconView.squashAnimator(),
                iconView.jumpAnimator(),
                iconView.settleAnimator(),
            )
        }
        val splashExit = splashView.exitAnimator()
        val contentReveal = contentView?.revealAnimator()

        val exitSet = AnimatorSet().apply {
            if (contentReveal == null) {
                playTogether(iconHop, splashExit)
            } else {
                playTogether(iconHop, splashExit, contentReveal)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = removeSplash()

                override fun onAnimationCancel(animation: Animator) = removeSplash()
            })
        }

        splashView.doOnDetach { exitSet.cancel() }
        exitSet.start()
    }

    private fun View.prepareForExitScale() {
        pivotX = width / 2f
        pivotY = height / 2f
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun View.prepareForReveal() {
        alpha = BiliTvLaunchMotion.CONTENT_START_ALPHA
        translationY = dp(BiliTvLaunchMotion.CONTENT_START_OFFSET_DP)
    }

    private fun View.squashAnimator(): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    this@squashAnimator,
                    View.TRANSLATION_Y,
                    0f,
                    dp(BiliTvLaunchMotion.ICON_SQUASH_OFFSET_DP),
                ),
                ObjectAnimator.ofFloat(
                    this@squashAnimator,
                    View.SCALE_X,
                    1f,
                    BiliTvLaunchMotion.ICON_SQUASH_SCALE_X,
                ),
                ObjectAnimator.ofFloat(
                    this@squashAnimator,
                    View.SCALE_Y,
                    1f,
                    BiliTvLaunchMotion.ICON_SQUASH_SCALE_Y,
                ),
                ObjectAnimator.ofFloat(
                    this@squashAnimator,
                    View.ROTATION,
                    0f,
                    BiliTvLaunchMotion.ICON_SQUASH_ROTATION_DEGREES,
                ),
            )
            duration = BiliTvLaunchMotion.SQUASH_DURATION_MILLIS.toLong()
            interpolator = standardEase
        }

    private fun View.jumpAnimator(): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    this@jumpAnimator,
                    View.TRANSLATION_Y,
                    dp(BiliTvLaunchMotion.ICON_SQUASH_OFFSET_DP),
                    dp(BiliTvLaunchMotion.ICON_JUMP_OFFSET_DP),
                ),
                ObjectAnimator.ofFloat(
                    this@jumpAnimator,
                    View.SCALE_X,
                    BiliTvLaunchMotion.ICON_SQUASH_SCALE_X,
                    BiliTvLaunchMotion.ICON_JUMP_SCALE_X,
                ),
                ObjectAnimator.ofFloat(
                    this@jumpAnimator,
                    View.SCALE_Y,
                    BiliTvLaunchMotion.ICON_SQUASH_SCALE_Y,
                    BiliTvLaunchMotion.ICON_JUMP_SCALE_Y,
                ),
                ObjectAnimator.ofFloat(
                    this@jumpAnimator,
                    View.ROTATION,
                    BiliTvLaunchMotion.ICON_SQUASH_ROTATION_DEGREES,
                    BiliTvLaunchMotion.ICON_JUMP_ROTATION_DEGREES,
                ),
            )
            duration = BiliTvLaunchMotion.JUMP_DURATION_MILLIS.toLong()
            interpolator = emphasizedEase
        }

    private fun View.settleAnimator(): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    this@settleAnimator,
                    View.TRANSLATION_Y,
                    dp(BiliTvLaunchMotion.ICON_JUMP_OFFSET_DP),
                    0f,
                ),
                ObjectAnimator.ofFloat(
                    this@settleAnimator,
                    View.SCALE_X,
                    BiliTvLaunchMotion.ICON_JUMP_SCALE_X,
                    1f,
                ),
                ObjectAnimator.ofFloat(
                    this@settleAnimator,
                    View.SCALE_Y,
                    BiliTvLaunchMotion.ICON_JUMP_SCALE_Y,
                    1f,
                ),
                ObjectAnimator.ofFloat(
                    this@settleAnimator,
                    View.ROTATION,
                    BiliTvLaunchMotion.ICON_JUMP_ROTATION_DEGREES,
                    0f,
                ),
            )
            duration = BiliTvLaunchMotion.SETTLE_DURATION_MILLIS.toLong()
            interpolator = settleEase
        }

    private fun View.exitAnimator(): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@exitAnimator, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(
                    this@exitAnimator,
                    View.SCALE_X,
                    1f,
                    BiliTvLaunchMotion.SPLASH_END_SCALE,
                ),
                ObjectAnimator.ofFloat(
                    this@exitAnimator,
                    View.SCALE_Y,
                    1f,
                    BiliTvLaunchMotion.SPLASH_END_SCALE,
                ),
            )
            startDelay = BiliTvLaunchMotion.SPLASH_FADE_DELAY_MILLIS.toLong()
            duration = BiliTvLaunchMotion.SPLASH_FADE_DURATION_MILLIS.toLong()
            interpolator = standardEase
        }

    private fun View.revealAnimator(): Animator =
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(
                    this@revealAnimator,
                    View.ALPHA,
                    BiliTvLaunchMotion.CONTENT_START_ALPHA,
                    1f,
                ),
                ObjectAnimator.ofFloat(
                    this@revealAnimator,
                    View.TRANSLATION_Y,
                    dp(BiliTvLaunchMotion.CONTENT_START_OFFSET_DP),
                    0f,
                ),
            )
            startDelay = BiliTvLaunchMotion.CONTENT_REVEAL_DELAY_MILLIS.toLong()
            duration = BiliTvLaunchMotion.CONTENT_REVEAL_DURATION_MILLIS.toLong()
            interpolator = contentEase
        }

    private fun View.dp(value: Float): Float =
        value * resources.displayMetrics.density
}
