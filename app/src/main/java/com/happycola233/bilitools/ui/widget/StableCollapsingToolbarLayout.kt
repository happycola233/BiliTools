package com.happycola233.bilitools.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * Keep the measured height aligned with the explicit XML height so transient
 * window inset changes from freeform/small-window transitions cannot stretch
 * the top bar container permanently.
 */
class StableCollapsingToolbarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : CollapsingToolbarLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val fixedHeight = layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
        if (fixedHeight > 0 && measuredHeight > fixedHeight) {
            setMeasuredDimension(measuredWidth, fixedHeight)
        }
    }
}
