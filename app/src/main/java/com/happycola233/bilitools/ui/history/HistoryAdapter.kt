package com.happycola233.bilitools.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.happycola233.bilitools.R
import com.happycola233.bilitools.data.model.HistoryItem
import com.happycola233.bilitools.databinding.ItemHistoryBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class HistoryAdapter(
    private val onDownload: (HistoryItem) -> Unit,
    private val onAuthor: (HistoryItem) -> Unit,
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding, onDownload, onAuthor)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class HistoryViewHolder(
        private val binding: ItemHistoryBinding,
        private val onDownload: (HistoryItem) -> Unit,
        private val onAuthor: (HistoryItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        private val zoneId = ZoneId.systemDefault()
        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(item: HistoryItem) {
            val context = binding.root.context

            val displayTitle = item.title.ifBlank {
                item.longTitle?.takeIf { it.isNotBlank() } ?: item.bvid.orEmpty()
            }
            binding.historyTitle.text = displayTitle

            binding.historyViewAt.text = context.getString(
                R.string.history_view_at_format,
                formatTimestamp(item.viewAt),
            )

            val duration = item.duration.coerceAtLeast(0)
            if (duration > 0) {
                val watched = if (item.progress < 0) duration else item.progress.coerceIn(0, duration)
                val progressPercent = (watched * 100f / duration).coerceIn(0f, 100f).roundToInt()
                binding.historyCoverProgress.visibility = View.VISIBLE
                binding.historyCoverProgress.progress = progressPercent
                binding.historyProgressText.visibility = View.VISIBLE
                binding.historyProgressText.text = if (item.progress < 0) {
                    context.getString(
                        R.string.history_progress_completed,
                        formatDuration(duration),
                    )
                } else {
                    context.getString(
                        R.string.history_progress_format,
                        formatDuration(watched),
                        formatDuration(duration),
                    )
                }
            } else {
                binding.historyCoverProgress.visibility = View.GONE
                binding.historyProgressText.visibility = View.GONE
            }

            val authorName = item.authorName.takeIf { it.isNotBlank() }
            if (authorName == null) {
                binding.historyAuthorRow.visibility = View.GONE
                binding.historyAuthorRow.setOnClickListener(null)
                binding.historyAuthor.setOnClickListener(null)
            } else {
                binding.historyAuthorRow.visibility = View.VISIBLE
                binding.historyAuthor.text = authorName
                binding.historyAuthorAvatar.load(item.authorAvatarUrl ?: R.drawable.default_avatar) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
                if (item.authorMid != null) {
                    val clickListener = View.OnClickListener { onAuthor(item) }
                    binding.historyAuthorRow.setOnClickListener(clickListener)
                    binding.historyAuthor.setOnClickListener(clickListener)
                } else {
                    binding.historyAuthorRow.setOnClickListener(null)
                    binding.historyAuthor.setOnClickListener(null)
                }
            }

            val canJumpDownload = !item.toParseUrl().isNullOrBlank()
            binding.historyDownload.visibility = if (canJumpDownload) View.VISIBLE else View.GONE
            binding.historyDownload.setOnClickListener(
                if (canJumpDownload) {
                    View.OnClickListener { onDownload(item) }
                } else {
                    null
                },
            )

            val coverUrl = item.displayCoverUrl
            if (coverUrl.isNullOrBlank()) {
                binding.historyCover.setImageDrawable(null)
            } else {
                binding.historyCover.load(coverUrl) {
                    crossfade(true)
                }
            }
        }

        private fun formatTimestamp(epochSeconds: Long): String {
            if (epochSeconds <= 0L) return "--"
            return runCatching {
                Instant.ofEpochSecond(epochSeconds)
                    .atZone(zoneId)
                    .format(timeFormatter)
            }.getOrDefault("--")
        }

        private fun formatDuration(seconds: Int): String {
            val safe = seconds.coerceAtLeast(0)
            val hours = safe / 3600
            val minutes = (safe % 3600) / 60
            val secs = safe % 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                val oldId = oldItem.bvid ?: oldItem.uri
                val newId = newItem.bvid ?: newItem.uri
                if (!oldId.isNullOrBlank() && !newId.isNullOrBlank()) {
                    return oldId == newId && oldItem.viewAt == newItem.viewAt
                }
                return oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}
