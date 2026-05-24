package com.examhub.student.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.databinding.ItemExamBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RecentExamAdapter(
    private val onItemClick: (Exam) -> Unit
) : ListAdapter<Exam, RecentExamAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemExamBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(exam: Exam, onClick: (Exam) -> Unit) {
            val context = binding.root.context
            binding.tvExamName.text = exam.name
            binding.tvClassName.text = listOf(exam.subject, exam.className)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            binding.tvExamProgress.text = exam.status
            binding.tvExamUpdated.text = formatExamDate(exam.date).ifBlank {
                context.getString(R.string.dashboard_exam_updated)
            }
            if (exam.hasSubmitted) {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_submitted)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_online)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_online_text))
            } else if (exam.isOfflineReady) {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_ready)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_ready)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_ready_text))
            } else {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_online)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_online)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_online_text))
            }
            binding.root.setOnClickListener { onClick(exam) }
        }

        private fun formatExamDate(raw: String): String {
            if (raw.isBlank()) return ""
            val normalized = raw.trim()
            val parsers = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                },
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            )
            val date = parsers.firstNotNullOfOrNull { parser ->
                runCatching { parser.parse(normalized) }.getOrNull()
            } ?: return normalized.removeSuffix("Z")
            return SimpleDateFormat("HH:mm dd/MM/yyyy", Locale("vi", "VN")).format(date)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Exam>() {
        override fun areItemsTheSame(oldItem: Exam, newItem: Exam) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Exam, newItem: Exam) = oldItem == newItem
    }
}
