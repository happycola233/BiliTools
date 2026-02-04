package com.happycola233.bilitools.ui.downloads

import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.RecyclerView

class StickyHeaderItemDecoration(private val adapter: StickyHeaderInterface) : RecyclerView.ItemDecoration(), RecyclerView.OnItemTouchListener {

    private var currentHeader: View? = null
    private var currentHeaderPosition: Int = RecyclerView.NO_POSITION
    private val headerCache = mutableMapOf<Int, View>()
    private var currentHeaderY: Float = 0f
    private var gestureDetector: GestureDetectorCompat? = null

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)

        val topChild = parent.getChildAt(0) ?: return
        val topChildPosition = parent.getChildAdapterPosition(topChild)

        if (topChildPosition == RecyclerView.NO_POSITION) return

        val headerPosition = adapter.getHeaderPositionForItem(topChildPosition)
        if (headerPosition == RecyclerView.NO_POSITION) {
            currentHeader = null
            currentHeaderPosition = RecyclerView.NO_POSITION
            return
        }

        // If the current top item is the header itself and it's not scrolled off-screen,
        // we don't need to draw the sticky header. This allows the real view to handle clicks and ripples.
        if (headerPosition == topChildPosition && topChild.top >= 0) {
            currentHeader = null
            currentHeaderPosition = RecyclerView.NO_POSITION
            return
        }

        val headerView = getHeaderView(parent, headerPosition)
        fixLayoutSize(parent, headerView)

        val contactPoint = headerView.bottom
        val childInContact = getChildInContact(parent, contactPoint)
        
        var moveY = 0
        if (childInContact != null && adapter.isHeader(parent.getChildAdapterPosition(childInContact))) {
             moveY = childInContact.top - headerView.height
        }

        c.save()
        c.translate(0f, moveY.toFloat())
        headerView.draw(c)
        c.restore()
        
        currentHeader = headerView
        currentHeaderPosition = headerPosition
        currentHeaderY = moveY.toFloat()
    }

    private fun getHeaderView(parent: RecyclerView, position: Int): View {
        val layoutResId = adapter.getHeaderLayout(position)
        var header = headerCache[layoutResId]
        if (header == null) {
            header = android.view.LayoutInflater.from(parent.context).inflate(layoutResId, parent, false)
            headerCache[layoutResId] = header
        }
        adapter.bindHeaderData(header, position)
        return header
    }

    private fun fixLayoutSize(parent: ViewGroup, view: View) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.UNSPECIFIED)

        val childWidthSpec = ViewGroup.getChildMeasureSpec(widthSpec, parent.paddingLeft + parent.paddingRight, view.layoutParams.width)
        val childHeightSpec = ViewGroup.getChildMeasureSpec(heightSpec, parent.paddingTop + parent.paddingBottom, view.layoutParams.height)

        view.measure(childWidthSpec, childHeightSpec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun getChildInContact(parent: RecyclerView, contactPoint: Int): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.bottom > 0 && child.top <= contactPoint) {
                if (child.top > 0) {
                     return child
                }
            }
        }
        return null
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (gestureDetector == null) {
            gestureDetector = GestureDetectorCompat(rv.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    val header = currentHeader ?: return false
                    if (e.y >= currentHeaderY && e.y <= currentHeaderY + header.height) {
                         adapter.onHeaderClicked(currentHeaderPosition)
                         return true
                    }
                    return false
                }
            })
        }
        return currentHeader != null && gestureDetector?.onTouchEvent(e) == true
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    }
}

interface StickyHeaderInterface {
    fun getHeaderPositionForItem(itemPosition: Int): Int
    fun getHeaderLayout(headerPosition: Int): Int
    fun bindHeaderData(header: View, headerPosition: Int)
    fun isHeader(itemPosition: Int): Boolean
    fun onHeaderClicked(headerPosition: Int)
}
