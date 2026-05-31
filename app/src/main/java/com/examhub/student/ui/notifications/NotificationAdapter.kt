package com.examhub.student.ui.notifications

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.AppNotification
import com.examhub.student.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class NotificationAdapter(
    private val onItemClick: (AppNotification) -> Unit,
    private val onMarkRead: (AppNotification) -> Unit
) : PagingDataAdapter<AppNotification, NotificationAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            notification: AppNotification,
            onClick: (AppNotification) -> Unit,
            onMarkRead: (AppNotification) -> Unit
        ) {
            val context = binding.root.context
            binding.tvNotificationTitle.text = notification.title.toReadableTitle(context, notification.type)
            binding.tvNotificationContent.text = notification.content.toReadableContent(context, notification.type)
            binding.tvNotificationTime.text = formatRelativeTime(context, notification.createdAt)
            binding.btnPrimaryAction.text = getActionText(context, notification)

            binding.ivNotificationType.setImageResource(getTypeIcon(notification.type))
            binding.ivNotificationType.setColorFilter(
                ContextCompat.getColor(context, getTypeColor(notification.type))
            )

            val unread = !notification.isRead
            binding.dotUnread.visibility = if (unread) View.VISIBLE else View.GONE
            binding.btnMarkRead.visibility = if (unread) View.VISIBLE else View.GONE
            binding.cardNotification.strokeColor = ContextCompat.getColor(
                context,
                if (unread) R.color.primary_container else R.color.divider_soft
            )
            binding.tvNotificationTitle.alpha = if (unread) 1f else 0.78f
            binding.tvNotificationContent.alpha = if (unread) 1f else 0.82f

            binding.root.setOnClickListener { onClick(notification) }
            binding.btnPrimaryAction.setOnClickListener { onClick(notification) }
            binding.btnMarkRead.setOnClickListener { onMarkRead(notification) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onItemClick, onMarkRead) }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification) = oldItem == newItem
    }

    companion object {
        private val isoFormatWithMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun formatRelativeTime(context: Context, isoTime: String): String {
            return try {
                val date = parseIsoDate(isoTime) ?: return isoTime
                val diffMs = Date().time - date.time
                val diffMin = diffMs / 60_000
                val diffHour = diffMin / 60
                val diffDay = diffHour / 24

                when {
                    diffMin < 1 -> context.getString(R.string.notifications_just_now)
                    diffMin < 60 -> context.getString(R.string.notifications_minutes_ago, diffMin)
                    diffHour < 24 -> context.getString(R.string.notifications_hours_ago, diffHour)
                    diffDay < 7 -> context.getString(R.string.notifications_days_ago, diffDay)
                    else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                isoTime
            }
        }

        private fun parseIsoDate(isoTime: String): Date? {
            if (isoTime.isBlank()) return null
            return runCatching { isoFormatWithMillis.parse(isoTime) }.getOrNull()
                ?: runCatching { isoFormat.parse(isoTime) }.getOrNull()
        }

        private fun getActionText(context: Context, notification: AppNotification): String {
            val type = notification.type.lowercase(Locale.ROOT)
            val link = notification.link.orEmpty().lowercase(Locale.ROOT)
            return if (type.contains("grade") || link.contains("result")) {
                context.getString(R.string.notifications_view_result)
            } else {
                context.getString(R.string.notifications_view_detail)
            }
        }

        private fun String.toReadableTitle(context: Context, type: String): String {
            val normalizedType = type.lowercase(Locale.ROOT)
            return when {
                normalizedType.contains("grade") && isBlank() -> context.getString(R.string.notifications_grade_default_title)
                else -> this
            }
        }

        private fun String.toReadableContent(context: Context, type: String): String {
            val normalizedType = type.lowercase(Locale.ROOT)
            return when {
                normalizedType.contains("grade") && contains(context.getString(R.string.notifications_grade_old_phrase), ignoreCase = true) ->
                    replace(
                        context.getString(R.string.notifications_grade_old_sentence),
                        context.getString(R.string.notifications_grade_new_sentence)
                    ).replace(
                        context.getString(R.string.notifications_grade_old_phrase),
                        context.getString(R.string.notifications_grade_new_phrase)
                    )
                else -> this
            }
        }

        fun getTypeIcon(type: String): Int = when (type.lowercase(Locale.ROOT)) {
            "submission_pending" -> R.drawable.ic_camera
            "appeal_new", "appeal_updated", "appeal_created", "appeal_resolved", "appeal_replied" -> R.drawable.ic_appeal
            "exam_closed", "exam_created" -> R.drawable.ic_dashboard
            "class_invite" -> R.drawable.ic_users
            "system" -> R.drawable.ic_settings
            else -> R.drawable.ic_notification
        }

        fun getTypeColor(type: String): Int = when (type.lowercase(Locale.ROOT)) {
            "submission_pending" -> R.color.primary
            "appeal_new", "appeal_updated", "appeal_created", "appeal_resolved", "appeal_replied" -> R.color.warning
            "exam_closed", "exam_created" -> R.color.tertiary
            "class_invite" -> R.color.secondary
            "system" -> R.color.text_secondary
            else -> R.color.primary
        }
    }
}
