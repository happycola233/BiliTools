package com.happycola233.bilitools.ui.downloads

import android.content.Context
import android.os.SystemClock
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import coil.load
import com.google.android.material.color.MaterialColors
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.DownloadGroup
import com.happycola233.bilitools.data.model.DownloadItem
import com.happycola233.bilitools.data.model.DownloadStatus
import com.happycola233.bilitools.data.model.DownloadTaskType
import com.happycola233.bilitools.databinding.ItemDownloadBinding
import com.happycola233.bilitools.databinding.ItemDownloadGroupBinding
import com.happycola233.bilitools.databinding.ItemDownloadsSectionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DownloadsListItem {
    data class SectionHeader(
        val type: DownloadSectionType,
        val count: Int,
        val speedBytesPerSec: Long = 0,
        val isCollapsed: Boolean = false,
    ) : DownloadsListItem()

    data class GroupItem(val group: DownloadGroup) : DownloadsListItem()
}

enum class DownloadSectionType {
    Downloading,
    Downloaded,
}

class DownloadsAdapter(
    private val onPauseResume: (DownloadItem) -> Unit,
    private val onRetry: (DownloadItem) -> Unit,
    private val onDelete: (DownloadItem) -> Unit,
    private val onTaskClick: (DownloadItem) -> Unit,
    private val onGroupPause: (DownloadGroup) -> Unit,
    private val onGroupResume: (DownloadGroup) -> Unit,
    private val onGroupDelete: (DownloadGroup, () -> Unit) -> Unit,
    private val onLocationClick: (String) -> Unit,
    private val onGroupSelectionToggle: (DownloadGroup) -> Unit,
) : ListAdapter<DownloadsListItem, RecyclerView.ViewHolder>(DownloadsDiffCallback), StickyHeaderInterface {

    internal val expandedGroups = mutableSetOf<Long>()
    private val collapsedSections = mutableSetOf<DownloadSectionType>()
    private var originalList: List<DownloadsListItem> = emptyList()
    private var selectionMode = false
    private var selectedGroupIds: Set<Long> = emptySet()

    // Swipe state
    private var swipedGroupId: Long? = null
    private var swipedViewHolder: GroupViewHolder? = null
    private val expandingGroups = mutableMapOf<Long, Long>()

    override fun getHeaderPositionForItem(itemPosition: Int): Int {
        var pos = itemPosition
        while (pos >= 0) {
            if (isHeader(pos)) return pos
            pos--
        }
        return RecyclerView.NO_POSITION
    }

    override fun getHeaderLayout(headerPosition: Int): Int {
        return R.layout.item_downloads_section
    }

    override fun bindHeaderData(header: View, headerPosition: Int) {
        if (headerPosition < 0 || headerPosition >= itemCount) return
        val item = getItem(headerPosition) as? DownloadsListItem.SectionHeader ?: return
        val binding = ItemDownloadsSectionBinding.bind(header)
        
        val context = header.context

        val typedValue = android.util.TypedValue()
        val theme = context.theme
        val resolved = theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        if (resolved) {
            header.setBackgroundColor(typedValue.data)
        } else {
             if (theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
                 header.setBackgroundColor(typedValue.data)
             }
        }
        
        val collapsed = item.isCollapsed
        
        val titleRes = when (item.type) {
            DownloadSectionType.Downloading -> R.string.downloads_section_downloading
            DownloadSectionType.Downloaded -> R.string.downloads_section_downloaded
        }
        binding.sectionTitle.text = context.getString(titleRes)
        binding.sectionMeta.text = if (item.type == DownloadSectionType.Downloading) {
            val speedText = context.getString(
                R.string.download_speed_format,
                formatBytes(item.speedBytesPerSec),
            )
            context.getString(
                R.string.downloads_section_count_speed,
                item.count,
                speedText,
            )
        } else {
            context.getString(R.string.downloads_section_count, item.count)
        }
        
        // Animate rotation for smooth effect
        val targetRotation = if (collapsed) -90f else 0f
        binding.sectionArrow.animate().rotation(targetRotation).setDuration(200).start()
    }

    override fun isHeader(itemPosition: Int): Boolean {
        if (itemPosition < 0 || itemPosition >= itemCount) return false
        return getItemViewType(itemPosition) == VIEW_TYPE_HEADER
    }
    
    override fun onHeaderClicked(headerPosition: Int) {
        if (selectionMode) return
        val item = getItem(headerPosition) as? DownloadsListItem.SectionHeader ?: return
        toggleSection(item.type)
    }
    
    private fun toggleSection(type: DownloadSectionType) {
        if (selectionMode) return
        if (!collapsedSections.add(type)) {
            collapsedSections.remove(type)
        }
        super.submitList(filterList(originalList))
    }

    fun submitFullList(list: List<DownloadsListItem>) {
        originalList = list
        super.submitList(filterList(list))
    }

    private fun filterList(list: List<DownloadsListItem>): List<DownloadsListItem> {
        val result = mutableListOf<DownloadsListItem>()
        var skipGroup = false
        for (item in list) {
            when (item) {
                is DownloadsListItem.SectionHeader -> {
                    val isCollapsed = collapsedSections.contains(item.type)
                    result.add(item.copy(isCollapsed = isCollapsed))
                    skipGroup = isCollapsed
                }
                is DownloadsListItem.GroupItem -> {
                    if (!skipGroup) {
                        result.add(item)
                    }
                }
            }
        }
        return result
    }

    fun updateSelectionState(enabled: Boolean, selectedIds: Set<Long>) {
        val nextSelected = selectedIds.toSet()
        if (selectionMode == enabled && selectedGroupIds == nextSelected) {
            return
        }
        val previousSelected = selectedGroupIds
        val modeChanged = selectionMode != enabled
        selectionMode = enabled
        selectedGroupIds = nextSelected
        if (selectionMode) {
            closeSwipedItem()
            if (expandedGroups.isNotEmpty()) {
                expandedGroups.clear()
                expandingGroups.clear()
            }
        }
        if (modeChanged) {
            currentList.forEachIndexed { index, item ->
                if (item is DownloadsListItem.GroupItem) {
                    notifyItemChanged(index, PAYLOAD_SELECTION_MODE_CHANGED)
                }
            }
            return
        }
        val changedGroupIds = (previousSelected - nextSelected) + (nextSelected - previousSelected)
        if (changedGroupIds.isEmpty()) return
        currentList.forEachIndexed { index, item ->
            if (item is DownloadsListItem.GroupItem && changedGroupIds.contains(item.group.id)) {
                notifyItemChanged(index, PAYLOAD_SELECTION_CHANGED)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is DownloadsListItem.SectionHeader -> VIEW_TYPE_HEADER
            is DownloadsListItem.GroupItem -> VIEW_TYPE_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val binding = ItemDownloadsSectionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false,
                )
                SectionHeaderViewHolder(binding) { type ->
                    toggleSection(type)
                }
            }
            else -> {
                val binding =
                    ItemDownloadGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                GroupViewHolder(
                    binding,
                    onPauseResume,
                    onRetry,
                    onDelete,
                    onTaskClick,
                    onGroupPause,
                    onGroupResume,
                    onGroupDelete,
                    onLocationClick,
                    this // Pass adapter to handle swipe coordination
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val updates = payloads.flatMap {
                if (it is List<*>) it else listOf(it)
            }.filterIsInstance<String>().toSet()
            val item = getItem(position)
            if (item is DownloadsListItem.SectionHeader && holder is SectionHeaderViewHolder) {
                if (updates.contains(PAYLOAD_COLLAPSED_CHANGED)) {
                    val collapsed = item.isCollapsed
                    val targetRotation = if (collapsed) -90f else 0f
                    holder.binding.sectionArrow.animate().rotation(targetRotation).setDuration(200).start()
                }

                if (updates.contains(PAYLOAD_META_CHANGED)) {
                    holder.bindMeta(item)
                }

                if (updates.isNotEmpty()) return
            }
            if (item is DownloadsListItem.GroupItem && holder is GroupViewHolder) {
                if (updates.contains(PAYLOAD_SELECTION_MODE_CHANGED)) {
                    holder.bindSelectionModeState(item.group)
                    return
                }
                if (updates.contains(PAYLOAD_SELECTION_CHANGED)) {
                    holder.bindSelectionState(item.group)
                    return
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DownloadsListItem.SectionHeader -> {
                (holder as SectionHeaderViewHolder).bind(item, item.isCollapsed)
            }
            is DownloadsListItem.GroupItem -> {
                val group = item.group
                val groupHolder = holder as GroupViewHolder
                val expanded = expandedGroups.contains(group.id)
                groupHolder.bind(group, expanded)
            }
        }
    }
    
    fun notifySwiped(holder: GroupViewHolder, groupId: Long?) {
        if (swipedGroupId != null && swipedGroupId != groupId) {
            closeSwipedItem()
        }
        swipedGroupId = groupId
        swipedViewHolder = if (groupId != null) holder else null
    }
    
    fun closeSwipedItem() {
        swipedViewHolder?.animateClose()
        swipedViewHolder = null
        swipedGroupId = null
    }

    fun clearSwipedState() {
        swipedViewHolder = null
        swipedGroupId = null
    }

    private fun getExpandRemainingMs(groupId: Long): Long {
        val until = expandingGroups[groupId] ?: return 0L
        val now = SystemClock.uptimeMillis()
        val remaining = until - now
        if (remaining <= 0L) {
            expandingGroups.remove(groupId)
            return 0L
        }
        return remaining
    }

    class GroupViewHolder(
        val binding: ItemDownloadGroupBinding,
        onPauseResume: (DownloadItem) -> Unit,
        onRetry: (DownloadItem) -> Unit,
        onDelete: (DownloadItem) -> Unit,
        onTaskClick: (DownloadItem) -> Unit,
        private val onGroupPause: (DownloadGroup) -> Unit,
        private val onGroupResume: (DownloadGroup) -> Unit,
        private val groupDeleteAction: (DownloadGroup, () -> Unit) -> Unit,
        private val onLocationClick: (String) -> Unit,
        private val adapter: DownloadsAdapter
    ) : RecyclerView.ViewHolder(binding.root) {
        private val taskAdapter =
            DownloadTaskAdapter(onPauseResume, onRetry, onDelete, onTaskClick, onLocationClick)
        private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        private val density = binding.root.context.resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop.toFloat()
        private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()
        private val swipeTarget = (80f * density) + (8f * density)
        private val snapThreshold = swipeTarget / 2
        private val dismissThreshold = 140f * density
        private val checkboxMarginEndPx = (8f * density).toInt()
        private val checkboxMinWidthPx = (32f * density).toInt()
        private var boundGroupId: Long = -1L
        private var revealDeleteRunnable: Runnable? = null
        private var currentGroup: DownloadGroup? = null
        private var downX = 0f
        private var downY = 0f
        private var isSwiping = false
        private var movedBeyondTouchSlop = false
        private var initialTranslationX = 0f
        private var touchActiveGroupId: Long? = null
        private var longPressTriggered = false
        private var longPressRunnable: Runnable? = null
        private var expandCollapseAnimator: ValueAnimator? = null
        private var checkboxWidthAnimator: ValueAnimator? = null
        private var checkboxAnimationTargetVisible: Boolean? = null
        private var cachedCheckboxExpandedWidthPx: Int = 0

        init {
            binding.groupTasks.layoutManager = LinearLayoutManager(binding.root.context)
            binding.groupTasks.adapter = taskAdapter
            (binding.groupTasks.itemAnimator as? SimpleItemAnimator)
                ?.supportsChangeAnimations = false
            setupSwipeTouchListener()
        }
        
        fun animateClose(hideDeleteDuringClose: Boolean = false) {
            binding.cardView.animate().cancel()
            if (hideDeleteDuringClose) {
                // Prevent occasional z-order flash of the delete button during close animation.
                binding.swipeDeleteContainer.alpha = 0f
            }
            val animator = binding.cardView.animate().translationX(0f).setDuration(200)
            if (hideDeleteDuringClose) {
                animator.withEndAction {
                    binding.swipeDeleteContainer.alpha = 1f
                }
            }
            animator.start()
        }

        private fun setupSwipeTouchListener() {
            binding.cardView.setOnTouchListener { v, event ->
                if (adapter.selectionMode) {
                    isSwiping = false
                    movedBeyondTouchSlop = false
                    touchActiveGroupId = null
                    cancelPendingLongPress()
                    return@setOnTouchListener false
                }
                val group = currentGroup ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        initialTranslationX = v.translationX
                        isSwiping = false
                        movedBeyondTouchSlop = false
                        longPressTriggered = false
                        touchActiveGroupId = group.id
                        // Close other if different
                        if (adapter.swipedGroupId != null && adapter.swipedGroupId != group.id) {
                            adapter.closeSwipedItem()
                            touchActiveGroupId = null
                            // Let this gesture fall through after closing the other item.
                            return@setOnTouchListener false
                        }
                        scheduleLongPress(group)
                        true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (longPressTriggered) {
                            return@setOnTouchListener true
                        }
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY

                        if (!movedBeyondTouchSlop &&
                            (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)
                        ) {
                            movedBeyondTouchSlop = true
                            cancelPendingLongPress()
                        }
                        
                        if (!isSwiping && Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 10 * density) {
                            isSwiping = true
                            cancelPendingLongPress()
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        }
                        
                        if (isSwiping) {
                            // Allow dragging left (negative)
                            // If starting from 0, max pull is unlimited (for dismiss)
                            // But usually we want some resistance if dragging right
                            var targetX = initialTranslationX + dx
                            if (targetX > 0) targetX = 0f // Cannot drag right past 0
                            
                            v.translationX = targetX
                        }
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        cancelPendingLongPress()
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        if (longPressTriggered) {
                            isSwiping = false
                            movedBeyondTouchSlop = false
                            touchActiveGroupId = null
                            longPressTriggered = false
                            return@setOnTouchListener true
                        }
                        if (isSwiping) {
                            val currentX = v.translationX
                            if (currentX < -dismissThreshold) {
                                // Trigger Dismiss
                                if (adapter.swipedGroupId == group.id) {
                                    animateClose(hideDeleteDuringClose = true)
                                    adapter.clearSwipedState()
                                } else {
                                    animateClose(hideDeleteDuringClose = true)
                                }
                                groupDeleteAction(group, { adapter.closeSwipedItem() })
                                // Optional: Animate out visually before refresh?
                                // Rely on Adapter refresh for now.
                            } else if (currentX < -snapThreshold) {
                                // Snap Open
                                v.animate().translationX(-swipeTarget).setDuration(200).start()
                                adapter.notifySwiped(this@GroupViewHolder, group.id)
                            } else {
                                // Snap Close
                                v.animate().translationX(0f).setDuration(200).start()
                                if (adapter.swipedGroupId == group.id) {
                                    adapter.closeSwipedItem()
                                }
                            }
                        } else {
                            // Expand/collapse only on ACTION_UP without scroll-like movement.
                            if (event.actionMasked == android.view.MotionEvent.ACTION_UP &&
                                !movedBeyondTouchSlop
                            ) {
                                if (adapter.swipedGroupId == group.id) {
                                    // If open, close
                                    adapter.closeSwipedItem()
                                } else {
                                    // Toggle Expand
                                    binding.cardView.performClick()
                                }
                            }
                        }
                        isSwiping = false
                        movedBeyondTouchSlop = false
                        touchActiveGroupId = null
                        true
                    }
                    else -> false
                }
            }
        }

        private fun scheduleLongPress(group: DownloadGroup) {
            cancelPendingLongPress()
            val runnable = Runnable {
                if (adapter.selectionMode) return@Runnable
                if (touchActiveGroupId != group.id) return@Runnable
                if (isSwiping || movedBeyondTouchSlop) return@Runnable
                longPressTriggered = true
                binding.cardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                adapter.onGroupSelectionToggle(group)
            }
            longPressRunnable = runnable
            binding.cardView.postDelayed(runnable, longPressTimeoutMs)
        }

        private fun cancelPendingLongPress() {
            longPressRunnable?.let { binding.cardView.removeCallbacks(it) }
            longPressRunnable = null
        }

        fun bind(group: DownloadGroup, expanded: Boolean) {
            boundGroupId = group.id
            currentGroup = group
            if (touchActiveGroupId != group.id) {
                longPressTriggered = false
                cancelPendingLongPress()
            }
            revealDeleteRunnable?.let { binding.cardView.removeCallbacks(it) }
            revealDeleteRunnable = null
            val context = binding.root.context
            val selectionMode = adapter.selectionMode
            val isSelected = adapter.selectedGroupIds.contains(group.id)
            updateSelectionCheckboxVisibility(selectionMode, animate = false)
            binding.groupSelect.isChecked = isSelected
            binding.groupSelect.setOnClickListener {
                if (selectionMode) {
                    adapter.onGroupSelectionToggle(group)
                }
            }
            binding.cardView.strokeColor = MaterialColors.getColor(
                binding.cardView,
                com.google.android.material.R.attr.colorPrimary,
            )
            binding.cardView.strokeWidth = if (isSelected) (2 * density).toInt() else 0
            val remainingExpandMs = adapter.getExpandRemainingMs(group.id)
            val isExpanding = remainingExpandMs > 0L
            binding.swipeDeleteContainer.alpha = 1f
            binding.swipeDeleteContainer.visibility = when {
                selectionMode -> View.GONE
                isExpanding -> View.INVISIBLE
                else -> View.VISIBLE
            }
            if (isExpanding && !selectionMode) {
                scheduleDeleteReveal(group.id, remainingExpandMs)
            }
            
            // Reset State
            if (touchActiveGroupId == group.id) {
                // Keep current swipe offset while this item is actively handling a gesture.
            } else if (selectionMode) {
                binding.cardView.translationX = 0f
            } else if (adapter.swipedGroupId == group.id) {
                binding.cardView.translationX = -swipeTarget
                adapter.notifySwiped(this, group.id)
            } else {
                binding.cardView.translationX = 0f
            }
            
            // Delete Button Action
            binding.swipeDeleteBtn.setOnClickListener {
                groupDeleteAction(group, { adapter.closeSwipedItem() })
            }
            
            // Standard Bind Logic ...
            binding.groupTitle.text = group.title

            val allMissing = group.tasks.isNotEmpty() && group.tasks.all {
                it.status == DownloadStatus.Success && it.outputMissing
            }
            if (allMissing) {
                binding.groupTitle.paintFlags = binding.groupTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.groupTitle.alpha = 0.6f
            } else {
                binding.groupTitle.paintFlags = binding.groupTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.groupTitle.alpha = 1.0f
            }
            
            if (group.bvid.isNullOrBlank()) {
                binding.groupBvid.visibility = View.GONE
            } else {
                binding.groupBvid.text = group.bvid
                binding.groupBvid.visibility = View.VISIBLE
            }
            
            val completed = group.tasks.count { it.status == DownloadStatus.Success }
            val progress = calculateGroupProgress(group.tasks)
            binding.groupSummary.text = context.getString(
                R.string.downloads_group_summary,
                completed,
                group.tasks.size,
                progress,
            )
            
            val dateStr = if (group.createdAt > 0) {
                timeFormatter.format(Date(group.createdAt))
            } else {
                context.getString(R.string.download_time_unknown)
            }
            binding.groupCreatedAt.text = context.getString(R.string.downloads_group_created, dateStr)

            val coverUrl = group.coverUrl?.trim().orEmpty()
            if (coverUrl.isBlank()) {
                binding.groupCover.setImageDrawable(null)
                binding.groupCover.setBackgroundResource(R.drawable.ic_launcher_background)
            } else {
                binding.groupCover.load(coverUrl) {
                    allowHardware(false)
                    crossfade(true)
                }
            }

            binding.groupProgress.progress = progress
            val allSuccess = group.tasks.all { it.status == DownloadStatus.Success }
            binding.groupProgress.visibility = if (allSuccess) View.GONE else View.VISIBLE

            val hasRunning = group.tasks.any { item ->
                isManaged(item) && when (item.status) {
                    DownloadStatus.Pending,
                    DownloadStatus.Running,
                    DownloadStatus.Merging -> true
                    else -> false
                }
            }
            val hasPaused = group.tasks.any { item ->
                isManaged(item) &&
                    item.status == DownloadStatus.Paused &&
                    item.userPaused
            }
            
            if (selectionMode) {
                binding.groupActionsContainer.visibility = View.GONE
            } else if (hasRunning) {
                binding.groupPauseResume.setIconResource(R.drawable.ic_pause_24)
                binding.groupPauseResume.text = context.getString(R.string.download_pause)
                binding.groupPauseResume.setOnClickListener { onGroupPause(group) }
                binding.groupActionsContainer.visibility = View.VISIBLE
            } else if (hasPaused) {
                binding.groupPauseResume.setIconResource(R.drawable.ic_play_arrow_24)
                binding.groupPauseResume.text = context.getString(R.string.download_resume)
                binding.groupPauseResume.setOnClickListener { onGroupResume(group) }
                binding.groupActionsContainer.visibility = View.VISIBLE
            } else {
                binding.groupActionsContainer.visibility = View.GONE
            }
            binding.groupPauseResume.setOnLongClickListener {
                if (adapter.selectionMode) return@setOnLongClickListener false
                binding.cardView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                adapter.onGroupSelectionToggle(group)
                true
            }
            
            val shouldExpand = expanded && !selectionMode
            applyExpandedStateImmediate(group, shouldExpand)
            binding.groupToggle.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE
            
            // Re-apply click listeners logic via standard SetOnClickListener for accessibility/fallback
            // But we primarily handle clicks in OnTouch. 
            // We just need to ensure performClick works.
            binding.cardView.setOnClickListener {
                if (adapter.selectionMode) {
                    adapter.onGroupSelectionToggle(group)
                    return@setOnClickListener
                }
                if (adapter.swipedGroupId != null) {
                    adapter.closeSwipedItem()
                    return@setOnClickListener
                }

                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val isExpanded = adapter.expandedGroups.contains(group.id)
                // Toggle state in data source
                if (isExpanded) {
                    adapter.expandedGroups.remove(group.id)
                    adapter.expandingGroups.remove(group.id)
                    binding.swipeDeleteContainer.visibility = View.VISIBLE
                } else {
                    adapter.expandedGroups.add(group.id)
                    adapter.expandingGroups[group.id] =
                        SystemClock.uptimeMillis() + EXPAND_ANIM_DURATION_MS
                    // Hide delete button during expansion to prevent flash
                    binding.swipeDeleteContainer.visibility = View.INVISIBLE
                    // Restore delete button visibility after animation
                    binding.cardView.postDelayed({
                        binding.swipeDeleteContainer.visibility = View.VISIBLE
                    }, EXPAND_ANIM_DURATION_MS)
                }

                val newExpanded = !isExpanded
                animateExpandedState(group, newExpanded)
            }
        }

        fun bindSelectionModeState(group: DownloadGroup) {
            val selectionMode = adapter.selectionMode
            val isSelected = adapter.selectedGroupIds.contains(group.id)
            updateSelectionCheckboxVisibility(selectionMode, animate = true)
            binding.groupSelect.isChecked = isSelected
            binding.groupSelect.setOnClickListener {
                if (selectionMode) {
                    adapter.onGroupSelectionToggle(group)
                }
            }
            binding.cardView.strokeColor = MaterialColors.getColor(
                binding.cardView,
                com.google.android.material.R.attr.colorPrimary,
            )
            binding.cardView.strokeWidth = if (isSelected) (2 * density).toInt() else 0

            val remainingExpandMs = adapter.getExpandRemainingMs(group.id)
            val isExpanding = remainingExpandMs > 0L
            binding.swipeDeleteContainer.alpha = 1f
            binding.swipeDeleteContainer.visibility = when {
                selectionMode -> View.GONE
                isExpanding -> View.INVISIBLE
                else -> View.VISIBLE
            }
            if (isExpanding && !selectionMode) {
                scheduleDeleteReveal(group.id, remainingExpandMs)
            }

            if (selectionMode) {
                binding.cardView.translationX = 0f
            } else if (adapter.swipedGroupId == group.id) {
                binding.cardView.translationX = -swipeTarget
                adapter.notifySwiped(this, group.id)
            } else {
                binding.cardView.translationX = 0f
            }

            val shouldExpand = adapter.expandedGroups.contains(group.id) && !selectionMode
            applyExpandedStateImmediate(group, shouldExpand)
            binding.groupToggle.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE
        }

        fun bindSelectionState(group: DownloadGroup) {
            val selectionMode = adapter.selectionMode
            val isSelected = adapter.selectedGroupIds.contains(group.id)
            updateSelectionCheckboxVisibility(selectionMode, animate = false)
            binding.groupSelect.isChecked = isSelected
            binding.groupSelect.setOnClickListener {
                if (selectionMode) {
                    adapter.onGroupSelectionToggle(group)
                }
            }
            binding.cardView.strokeColor = MaterialColors.getColor(
                binding.cardView,
                com.google.android.material.R.attr.colorPrimary,
            )
            binding.cardView.strokeWidth = if (isSelected) (2 * density).toInt() else 0
        }

        private fun applyExpandedStateImmediate(group: DownloadGroup, expanded: Boolean) {
            expandCollapseAnimator?.cancel()
            expandCollapseAnimator = null

            binding.groupToggle.rotation = if (expanded) 180f else 0f
            binding.expandedContent.layoutParams =
                binding.expandedContent.layoutParams.apply {
                    height = if (expanded) ViewGroup.LayoutParams.WRAP_CONTENT else 0
                }
            binding.expandedContent.visibility = if (expanded) View.VISIBLE else View.GONE

            if (expanded) {
                taskAdapter.groupPath = group.relativePath
                taskAdapter.submitList(group.tasks)
            } else {
                taskAdapter.submitList(emptyList())
            }
        }

        private fun updateSelectionCheckboxVisibility(
            visible: Boolean,
            animate: Boolean,
        ) {
            val checkbox = binding.groupSelect
            val runningAnimator = checkboxWidthAnimator
            val sameTargetAnimationRunning =
                runningAnimator?.isRunning == true && checkboxAnimationTargetVisible == visible
            if (sameTargetAnimationRunning) {
                return
            }
            checkbox.animate().cancel()
            runningAnimator?.cancel()
            checkboxWidthAnimator = null
            checkboxAnimationTargetVisible = null

            val layoutParams = checkbox.layoutParams as? LinearLayout.LayoutParams ?: return
            val targetWidth = if (visible) resolveCheckboxExpandedWidthPx(layoutParams) else 0
            val targetMarginEnd = if (visible) checkboxMarginEndPx else 0
            val targetAlpha = if (visible) 1f else 0f

            if (!animate) {
                checkbox.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                layoutParams.width = targetWidth
                layoutParams.marginEnd = targetMarginEnd
                checkbox.layoutParams = layoutParams
                checkbox.alpha = targetAlpha
                checkbox.isEnabled = visible
                checkbox.isClickable = visible
                return
            }

            val startWidth = when {
                layoutParams.width >= 0 -> layoutParams.width
                checkbox.width > 0 -> checkbox.width
                visible -> 0
                else -> targetWidth
            }
            val startMarginEnd = layoutParams.marginEnd
            val startAlpha = checkbox.alpha

            checkbox.visibility = View.VISIBLE
            checkbox.isEnabled = visible
            checkbox.isClickable = visible

            checkboxAnimationTargetVisible = visible
            checkboxWidthAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (visible) CHECKBOX_ANIM_IN_DURATION_MS else CHECKBOX_ANIM_OUT_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    layoutParams.width = lerpInt(startWidth, targetWidth, fraction)
                    layoutParams.marginEnd = lerpInt(startMarginEnd, targetMarginEnd, fraction)
                    checkbox.layoutParams = layoutParams
                    checkbox.alpha = startAlpha + (targetAlpha - startAlpha) * fraction
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var canceled = false

                    override fun onAnimationCancel(animation: Animator) {
                        canceled = true
                        checkboxAnimationTargetVisible = null
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        checkboxAnimationTargetVisible = null
                        if (canceled) return
                        layoutParams.width = targetWidth
                        layoutParams.marginEnd = targetMarginEnd
                        checkbox.layoutParams = layoutParams
                        checkbox.alpha = targetAlpha
                        if (!visible) {
                            checkbox.visibility = View.INVISIBLE
                        }
                    }
                })
                start()
            }
        }

        private fun resolveCheckboxExpandedWidthPx(
            layoutParams: LinearLayout.LayoutParams,
        ): Int {
            if (cachedCheckboxExpandedWidthPx > 0) {
                return cachedCheckboxExpandedWidthPx
            }

            val checkbox = binding.groupSelect
            val oldWidth = layoutParams.width
            val oldMarginEnd = layoutParams.marginEnd
            val oldVisibility = checkbox.visibility
            val oldAlpha = checkbox.alpha

            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            layoutParams.marginEnd = checkboxMarginEndPx
            checkbox.layoutParams = layoutParams
            checkbox.visibility = View.VISIBLE
            checkbox.alpha = 1f

            val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val heightSpec = if (binding.groupHeader.height > 0) {
                View.MeasureSpec.makeMeasureSpec(binding.groupHeader.height, View.MeasureSpec.AT_MOST)
            } else {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            }
            checkbox.measure(widthSpec, heightSpec)
            cachedCheckboxExpandedWidthPx = checkbox.measuredWidth.coerceAtLeast(checkboxMinWidthPx)

            layoutParams.width = oldWidth
            layoutParams.marginEnd = oldMarginEnd
            checkbox.layoutParams = layoutParams
            checkbox.visibility = oldVisibility
            checkbox.alpha = oldAlpha

            return cachedCheckboxExpandedWidthPx
        }

        private fun lerpInt(start: Int, end: Int, fraction: Float): Int {
            return (start + (end - start) * fraction).toInt()
        }

        private fun animateExpandedState(group: DownloadGroup, expanded: Boolean) {
            binding.groupToggle.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(EXPAND_ANIM_DURATION_MS)
                .start()

            if (expanded) {
                taskAdapter.groupPath = group.relativePath
                taskAdapter.submitList(group.tasks) {
                    binding.expandedContent.post {
                        if (boundGroupId != group.id) return@post
                        animateExpandContainer(expand = true)
                    }
                }
            } else {
                animateExpandContainer(expand = false) {
                    if (boundGroupId == group.id) {
                        taskAdapter.submitList(emptyList())
                    }
                }
            }
        }

        private fun animateExpandContainer(
            expand: Boolean,
            onEnd: (() -> Unit)? = null,
        ) {
            expandCollapseAnimator?.cancel()

            val content = binding.expandedContent
            val layoutParams = content.layoutParams
            val startHeight = if (content.visibility == View.VISIBLE) content.height else 0

            if (expand) {
                content.visibility = View.VISIBLE
                layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                content.layoutParams = layoutParams
                val targetHeight = measureExpandedContentHeight()
                if (targetHeight <= 0) {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    content.layoutParams = layoutParams
                    onEnd?.invoke()
                    return
                }
                layoutParams.height = if (startHeight > 0) startHeight else 0
                content.layoutParams = layoutParams
                content.requestLayout()
                expandCollapseAnimator = ValueAnimator.ofInt(layoutParams.height, targetHeight).apply {
                    duration = EXPAND_ANIM_DURATION_MS
                    interpolator = FastOutSlowInInterpolator()
                    addUpdateListener { animator ->
                        layoutParams.height = animator.animatedValue as Int
                        content.layoutParams = layoutParams
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        private var canceled = false

                        override fun onAnimationCancel(animation: Animator) {
                            canceled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (canceled) return
                            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                            content.layoutParams = layoutParams
                            onEnd?.invoke()
                        }
                    })
                    start()
                }
                return
            }

            val measuredStart = if (startHeight > 0) startHeight else measureExpandedContentHeight()
            if (measuredStart <= 0) {
                content.visibility = View.GONE
                layoutParams.height = 0
                content.layoutParams = layoutParams
                onEnd?.invoke()
                return
            }

            layoutParams.height = measuredStart
            content.layoutParams = layoutParams
            expandCollapseAnimator = ValueAnimator.ofInt(measuredStart, 0).apply {
                duration = EXPAND_ANIM_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { animator ->
                    layoutParams.height = animator.animatedValue as Int
                    content.layoutParams = layoutParams
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var canceled = false

                    override fun onAnimationCancel(animation: Animator) {
                        canceled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (canceled) return
                        content.visibility = View.GONE
                        layoutParams.height = 0
                        content.layoutParams = layoutParams
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }

        private fun measureExpandedContentHeight(): Int {
            val content = binding.expandedContent
            val parentWidth = binding.cardView.width
            if (parentWidth <= 0) return content.measuredHeight
            val widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            content.measure(widthSpec, heightSpec)
            return content.measuredHeight
        }

        private fun scheduleDeleteReveal(groupId: Long, delayMs: Long) {
            if (delayMs <= 0L) {
                binding.swipeDeleteContainer.visibility = View.VISIBLE
                return
            }
            val runnable = Runnable {
                if (boundGroupId == groupId) {
                    adapter.expandingGroups.remove(groupId)
                    binding.swipeDeleteContainer.visibility = View.VISIBLE
                }
            }
            revealDeleteRunnable = runnable
            binding.cardView.postDelayed(runnable, delayMs)
        }

        private fun calculateGroupProgress(tasks: List<DownloadItem>): Int {
            if (tasks.isEmpty()) return 0
            val sizeTasks = tasks.filter { it.totalBytes > 0 }
            if (sizeTasks.isEmpty()) {
                val avg = tasks.sumOf { it.progress } / tasks.size
                val progress = avg.coerceIn(0, 100)
                return adjustMergingProgress(progress, tasks)
            }
            val total = sizeTasks.sumOf { it.totalBytes }
            if (total <= 0L) return 0
            val downloaded = sizeTasks.sumOf { item ->
                item.downloadedBytes.coerceAtMost(item.totalBytes)
            }
            val progress = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
            return adjustMergingProgress(progress, tasks)
        }

        private fun adjustMergingProgress(progress: Int, tasks: List<DownloadItem>): Int {
            return if (progress >= 100 && tasks.any { it.status == DownloadStatus.Merging }) {
                99
            } else {
                progress
            }
        }

        private fun isManaged(item: DownloadItem): Boolean {
            return when (item.taskType) {
                DownloadTaskType.Video,
                DownloadTaskType.Audio,
                DownloadTaskType.AudioVideo -> true
                else -> false
            }
        }
    }

    class SectionHeaderViewHolder(
        val binding: ItemDownloadsSectionBinding,
        private val onClick: (DownloadSectionType) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DownloadsListItem.SectionHeader, collapsed: Boolean) {
            bindMeta(item)
            
            binding.sectionArrow.rotation = if (collapsed) -90f else 0f
            
            binding.root.setOnClickListener { onClick(item.type) }
        }

        fun bindMeta(item: DownloadsListItem.SectionHeader) {
            val context = binding.root.context
            val titleRes = when (item.type) {
                DownloadSectionType.Downloading -> R.string.downloads_section_downloading
                DownloadSectionType.Downloaded -> R.string.downloads_section_downloaded
            }
            binding.sectionTitle.text = context.getString(titleRes)
            binding.sectionMeta.text = if (item.type == DownloadSectionType.Downloading) {
                val speedText = context.getString(
                    R.string.download_speed_format,
                    formatBytes(item.speedBytesPerSec),
                )
                context.getString(
                    R.string.downloads_section_count_speed,
                    item.count,
                    speedText,
                )
            } else {
                context.getString(R.string.downloads_section_count, item.count)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_GROUP = 1
        private const val EXPAND_ANIM_DURATION_MS = 200L
        private const val CHECKBOX_ANIM_IN_DURATION_MS = 240L
        private const val CHECKBOX_ANIM_OUT_DURATION_MS = 200L
        private const val PAYLOAD_SELECTION_MODE_CHANGED = "SELECTION_MODE_CHANGED"
        private const val PAYLOAD_COLLAPSED_CHANGED = "COLLAPSED_CHANGED"
        private const val PAYLOAD_META_CHANGED = "META_CHANGED"
        private const val PAYLOAD_SELECTION_CHANGED = "SELECTION_CHANGED"

        private val DownloadsDiffCallback = object : DiffUtil.ItemCallback<DownloadsListItem>() {
            override fun areItemsTheSame(
                oldItem: DownloadsListItem,
                newItem: DownloadsListItem,
            ): Boolean {
                return when {
                    oldItem is DownloadsListItem.SectionHeader &&
                        newItem is DownloadsListItem.SectionHeader ->
                        oldItem.type == newItem.type
                    oldItem is DownloadsListItem.GroupItem &&
                        newItem is DownloadsListItem.GroupItem ->
                        oldItem.group.id == newItem.group.id
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: DownloadsListItem,
                newItem: DownloadsListItem,
            ): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: DownloadsListItem, newItem: DownloadsListItem): Any? {
                if (oldItem is DownloadsListItem.SectionHeader && newItem is DownloadsListItem.SectionHeader) {
                    val payloads = mutableListOf<String>()
                    if (oldItem.isCollapsed != newItem.isCollapsed) {
                        payloads.add(PAYLOAD_COLLAPSED_CHANGED)
                    }
                    if (oldItem.count != newItem.count || oldItem.speedBytesPerSec != newItem.speedBytesPerSec) {
                        payloads.add(PAYLOAD_META_CHANGED)
                    }
                    if (payloads.isNotEmpty()) return payloads
                }
                return super.getChangePayload(oldItem, newItem)
            }
        }
    }
}

private class DownloadTaskAdapter(
    private val onPauseResume: (DownloadItem) -> Unit,
    private val onRetry: (DownloadItem) -> Unit,
    private val onDelete: (DownloadItem) -> Unit,
    private val onTaskClick: (DownloadItem) -> Unit,
    private val onLocationClick: (String) -> Unit,
) : ListAdapter<DownloadItem, DownloadTaskAdapter.TaskViewHolder>(TaskDiffCallback) {

    var groupPath: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding =
            ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding, onPauseResume, onRetry, onDelete, onTaskClick, onLocationClick)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position), groupPath)
    }

    class TaskViewHolder(
        private val binding: ItemDownloadBinding,
        private val onPauseResume: (DownloadItem) -> Unit,
        private val onRetry: (DownloadItem) -> Unit,
        private val onDelete: (DownloadItem) -> Unit,
        private val onTaskClick: (DownloadItem) -> Unit,
        private val onLocationClick: (String) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: DownloadItem, groupPath: String) {
            val context = binding.root.context
            
            binding.downloadTitle.text = item.title.ifBlank { item.fileName }

            val isMissing = item.status == DownloadStatus.Success && item.outputMissing
            if (isMissing) {
                binding.downloadTitle.paintFlags = binding.downloadTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.downloadTitle.alpha = 0.6f
                binding.downloadDetail.alpha = 0.6f
            } else {
                binding.downloadTitle.paintFlags = binding.downloadTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.downloadTitle.alpha = 1.0f
                binding.downloadDetail.alpha = 1.0f
            }
            
            binding.downloadDetail.text = buildDetailText(context, item)

            val isProcessing = item.status == DownloadStatus.Pending || item.status == DownloadStatus.Merging
            binding.downloadProgress.isIndeterminate = isProcessing
            
            val showProgress = when(item.status) {
                DownloadStatus.Pending, 
                DownloadStatus.Running, 
                DownloadStatus.Paused, 
                DownloadStatus.Merging -> true
                else -> false
            }
            binding.downloadProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
            binding.downloadProgress.progress = item.progress

            val managed = isManaged(item)
            val actionType = if (managed) {
                when (item.status) {
                    DownloadStatus.Running -> TaskAction.Pause
                    DownloadStatus.Paused -> if (item.userPaused) TaskAction.Resume else null
                    DownloadStatus.Failed -> TaskAction.Retry
                    else -> null
                }
            } else {
                null
            }

            if (actionType != null) {
                binding.downloadPauseResume.visibility = View.VISIBLE
                when (actionType) {
                    TaskAction.Pause -> {
                        binding.downloadPauseResume.setIconResource(R.drawable.ic_pause_24)
                        binding.downloadPauseResume.contentDescription =
                            context.getString(R.string.download_pause)
                        binding.downloadPauseResume.setOnClickListener { onPauseResume(item) }
                    }
                    TaskAction.Resume -> {
                        binding.downloadPauseResume.setIconResource(R.drawable.ic_play_arrow_24)
                        binding.downloadPauseResume.contentDescription =
                            context.getString(R.string.download_resume)
                        binding.downloadPauseResume.setOnClickListener { onPauseResume(item) }
                    }
                    TaskAction.Retry -> {
                        binding.downloadPauseResume.setIconResource(R.drawable.ic_retry_24)
                        binding.downloadPauseResume.contentDescription =
                            context.getString(R.string.download_retry)
                        binding.downloadPauseResume.setOnClickListener { onRetry(item) }
                    }
                }
            } else {
                binding.downloadPauseResume.visibility = View.GONE
            }

            binding.downloadDelete.setOnClickListener { onDelete(item) }
            if (isMissing) {
                binding.root.setOnClickListener(null)
            } else {
                binding.root.setOnClickListener { onTaskClick(item) }
            }
        }

        private fun buildDetailText(context: Context, item: DownloadItem): String {
            val downloaded = formatBytes(item.downloadedBytes)
            val sizeText = if (item.totalBytes > 0) {
                val total = formatBytes(item.totalBytes)
                context.getString(
                    R.string.download_size_progress,
                    downloaded,
                    total,
                )
            } else {
                context.getString(
                    R.string.download_size_downloaded,
                    downloaded,
                )
            }
            val baseText = when (item.status) {
                DownloadStatus.Running -> {
                    val speedText = if (item.speedBytesPerSec > 0) {
                        context.getString(
                            R.string.download_speed_format,
                            formatBytes(item.speedBytesPerSec),
                        )
                    } else {
                         ""
                    }
                    if (speedText.isNotBlank()) "$sizeText - $speedText" else sizeText
                }
                DownloadStatus.Pending -> context.getString(R.string.download_status_pending)
                DownloadStatus.Paused -> context.getString(R.string.download_status_paused, item.progress)
                DownloadStatus.Failed -> context.getString(R.string.download_status_failed)
                DownloadStatus.Merging -> context.getString(R.string.download_status_merging)
                DownloadStatus.Success -> if (item.outputMissing) {
                    context.getString(R.string.download_status_missing)
                } else {
                    context.getString(R.string.download_status_success)
                }
                DownloadStatus.Cancelled -> context.getString(R.string.download_status_cancelled)
            }
            val params = buildMediaParams(context, item)
            return if (params.isNullOrBlank()) {
                baseText
            } else {
                "$baseText\n${context.getString(R.string.download_params, params)}"
            }
        }

        private fun buildMediaParams(context: Context, item: DownloadItem): String? {
            val type = item.taskType
            if (type != DownloadTaskType.Video &&
                type != DownloadTaskType.Audio &&
                type != DownloadTaskType.AudioVideo) {
                return null
            }
            val params = item.mediaParams
            if (params != null) {
                val parts = listOfNotNull(
                    params.resolution?.takeIf { it.isNotBlank() },
                    params.codec?.takeIf { it.isNotBlank() },
                    params.audioBitrate?.takeIf { it.isNotBlank() },
                )
                if (parts.isNotEmpty()) {
                    return parts.joinToString(" / ")
                }
            }
            val fileName = item.fileName
            val name = when {
                fileName.endsWith(".mp4", ignoreCase = true) -> fileName.dropLast(4)
                fileName.endsWith(".flv", ignoreCase = true) -> fileName.dropLast(4)
                fileName.endsWith(".m4s", ignoreCase = true) -> fileName.dropLast(4)
                fileName.endsWith(".m4a", ignoreCase = true) -> fileName.dropLast(4)
                else -> fileName
            }
            if (name.isBlank()) return null
            val parts = name.split("-").filter { it.isNotBlank() }
            if (parts.isEmpty()) return null
            return when (type) {
                DownloadTaskType.Audio -> parts.lastOrNull()
                DownloadTaskType.Video,
                DownloadTaskType.AudioVideo -> {
                    val last = parts.lastOrNull() ?: return null
                    val codecs = setOf(
                        context.getString(R.string.parse_codec_avc),
                        context.getString(R.string.parse_codec_hevc),
                        context.getString(R.string.parse_codec_av1),
                    )
                    if (codecs.contains(last)) {
                        val resolution = parts.dropLast(1).lastOrNull()
                        if (resolution.isNullOrBlank()) {
                            last
                        } else {
                            "$resolution / $last"
                        }
                    } else {
                        last
                    }
                }
                else -> null
            }
        }

        private fun isManaged(item: DownloadItem): Boolean {
            return when (item.taskType) {
                DownloadTaskType.Video,
                DownloadTaskType.Audio,
                DownloadTaskType.AudioVideo -> true
                else -> false
            }
        }

        private enum class TaskAction {
            Pause,
            Resume,
            Retry,
        }
    }

    companion object {
        private val TaskDiffCallback = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
