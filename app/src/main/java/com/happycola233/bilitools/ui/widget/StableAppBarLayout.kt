package com.happycola233.bilitools.ui.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.appbar.AppBarLayout

/**
 * Keep the app bar height equal to its top padding plus the explicit fixed
 * child height so transient freeform-window measurements cannot leave the
 * whole page shifted downward.
 */
class StableAppBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppBarLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val fixedChildHeight = (0 until childCount)
            .mapNotNull { index ->
                val height = getChildAt(index).layoutParams?.height
                if (height != null && height > 0) height else null
            }
            .firstOrNull()
            ?: return

        val targetHeight = paddingTop + paddingBottom + fixedChildHeight
        if (measuredHeight > targetHeight) {
            setMeasuredDimension(measuredWidth, targetHeight)
        }
    }
}
