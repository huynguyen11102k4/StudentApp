package com.omr.scanner.student.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omr.scanner.student.R
import com.omr.scanner.student.data.model.AppNotification
import com.omr.scanner.student.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationAdapter(
    private val onItemClick: (AppNotification) -> Unit,
    private val onMarkRead: (AppNotification) -> Unit
) : ListAdapter<AppNotification, NotificationAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            notification: AppNotification,
            onClick: (AppNotification) -> Unit,
            onMarkRead: (AppNotification) -> Unit
        ) {
            val ctx = binding.root.context
            binding.tvNotificationTitle.text = notification.title
            binding.tvNotificationContent.text = notification.content
            binding.tvNotificationTime.text = formatRelativeTime(notification.createdAt)

            // Type icon
            binding.ivNotificationType.setImageResource(getTypeIcon(notification.type))
            binding.ivNotificationType.setColorFilter(
                ContextCompat.getColor(ctx, getTypeColor(notification.type))
            )

            // Unread indicator
            binding.dotUnread.visibility = if (notification.isRead) android.view.View.GONE else android.view.View.VISIBLE
            binding.root.alpha = if (notification.isRead) 0.6f else 1.0f
            binding.root.setOnClickListener { onClick(notification) }
            binding.btnMarkRead.setOnClickListener { onMarkRead(notification) }
            binding.btnMarkRead.visibility = if (notification.isRead) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onMarkRead)
    }

    class DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
    }

    companion object {
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun formatRelativeTime(isoTime: String): String {
            return try {
                val date = isoFormat.parse(isoTime) ?: return isoTime
                val now = Date()
                val diffMs = now.time - date.time
                val diffMin = diffMs / 60000
                val diffHour = diffMin / 60
                val diffDay = diffHour / 24

                when {
                    diffMin < 1 -> "Vừa xong"
                    diffMin < 60 -> "${diffMin} phút trước"
                    diffHour < 24 -> "${diffHour} giờ trước"
                    diffDay < 7 -> "${diffDay} ngày trước"
                    else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                isoTime
            }
        }

        fun getTypeIcon(type: String): Int = when (type.lowercase(Locale.ROOT)) {
            "submission_pending" -> R.drawable.ic_camera
            "appeal_new", "appeal_updated", "appeal_created" -> R.drawable.ic_appeal
            "exam_closed", "exam_created" -> R.drawable.ic_dashboard
            "class_invite" -> R.drawable.ic_users
            "system" -> R.drawable.ic_settings
            else -> R.drawable.ic_notification
        }

        fun getTypeColor(type: String): Int = when (type.lowercase(Locale.ROOT)) {
            "submission_pending" -> R.color.primary
            "appeal_new", "appeal_updated", "appeal_created" -> R.color.error
            "exam_closed", "exam_created" -> R.color.tertiary
            "class_invite" -> R.color.secondary
            "system" -> R.color.text_secondary
            else -> R.color.text_secondary
        }
    }
}
