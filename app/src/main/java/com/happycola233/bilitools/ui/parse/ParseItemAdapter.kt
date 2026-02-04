package com.happycola233.bilitools.ui.parse

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.happycola233.bilitools.databinding.ItemParseListBinding
import com.happycola233.bilitools.data.model.MediaItem
import com.google.android.material.R as MaterialR

class ParseItemAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onItemCheckToggle: (Int, Boolean) -> Unit,
) : ListAdapter<MediaItem, ParseItemAdapter.ItemViewHolder>(DiffCallback) {

    private var selectedIndices: Set<Int> = emptySet()
    private var currentIndex: Int = -1
    private var rowClickEnabled: Boolean = false

    fun updateSelection(selected: List<Int>, current: Int) {
        val nextSelected = selected.toSet()
        val prevSelected = selectedIndices
        val prevCurrent = currentIndex

        if (nextSelected == prevSelected && current == prevCurrent) return

        selectedIndices = nextSelected
        currentIndex = current

        val changed = HashSet<Int>()
        
        // Add items whose selection state changed
        // (Items in prev but not in next) OR (Items in next but not in prev)
        val selectionChanged = prevSelected.toMutableSet().apply {
            // Calculate symmetric difference
            val intersection = this.intersect(nextSelected)
            this.removeAll(intersection) // Items only in prev
            val onlyInNext = nextSelected.minus(intersection) // Items only in next
            addAll(onlyInNext)
        }
        changed.addAll(selectionChanged)

        // Add items whose current status changed
        if (prevCurrent != current) {
            changed.add(prevCurrent)
            changed.add(current)
        }

        val validChanges = changed.filter { it in 0 until itemCount }
        if (validChanges.isEmpty()) return
        validChanges.forEach { notifyItemChanged(it) }
    }

    fun setRowClickEnabled(enabled: Boolean) {
        if (rowClickEnabled == enabled) return
        rowClickEnabled = enabled
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemParseListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ItemViewHolder(binding, onItemClick, onItemCheckToggle)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            position,
            selectedIndices.contains(position),
            position == currentIndex,
            rowClickEnabled,
        )
    }

    class ItemViewHolder(
        private val binding: ItemParseListBinding,
        private val onItemClick: (Int) -> Unit,
        private val onItemCheckToggle: (Int, Boolean) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: MediaItem,
            position: Int,
            selected: Boolean,
            current: Boolean,
            rowClickEnabled: Boolean,
        ) {
            binding.itemIndex.text = (position + 1).toString()
            binding.itemTitle.text = item.title

            binding.itemCheck.setOnCheckedChangeListener(null)
            binding.itemCheck.isChecked = selected
            binding.itemCheck.setOnCheckedChangeListener { _, isChecked ->
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemCheckToggle(pos, isChecked)
                }
            }

            val cardColor = if (rowClickEnabled && current) {
                MaterialColors.getColor(binding.itemCard, MaterialR.attr.colorPrimary)
            } else {
                MaterialColors.getColor(binding.itemCard, MaterialR.attr.colorOutlineVariant)
            }
            binding.itemCard.strokeColor = cardColor

            if (rowClickEnabled) {
                binding.itemRoot.isClickable = true
                binding.itemRoot.isFocusable = true
                binding.itemRoot.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemClick(pos)
                    }
                }
            } else {
                binding.itemRoot.setOnClickListener(null)
                binding.itemRoot.isClickable = false
                binding.itemRoot.isFocusable = false
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.aid == newItem.aid &&
                oldItem.cid == newItem.cid &&
                oldItem.index == newItem.index &&
                oldItem.title == newItem.title
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}
