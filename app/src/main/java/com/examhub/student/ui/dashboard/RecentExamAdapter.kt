package com.examhub.student.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.databinding.ItemExamBinding
import com.examhub.student.util.extension.toLocalDisplayDateTime
import com.examhub.student.util.extension.toFriendlyExamStatus
import java.util.Locale

class RecentExamAdapter(
    private val onItemClick: (Exam) -> Unit
) : ListAdapter<Exam, RecentExamAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemExamBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(exam: Exam, onClick: (Exam) -> Unit) {
            val context = binding.root.context
            binding.tvExamName.text = exam.name
            binding.tvClassName.text = listOf(exam.subject, exam.className)
                .filter { it.isNotBlank() }
                .joinToString(binding.root.context.getString(R.string.common_separator_dot))
            binding.tvExamProgress.text = exam.status.ifBlank {
                context.getString(R.string.exam_status_open)
            }.toFriendlyExamStatus()
            binding.tvExamUpdated.text = exam.date.toLocalDisplayDateTime().ifBlank {
                context.getString(R.string.dashboard_exam_updated)
            }
            if (exam.hasSubmitted || exam.resultOnly) {
                if (exam.canViewResult && !exam.resultSheetId.isNullOrBlank()) {
                    binding.tvOfflineBadge.setText(R.string.dashboard_status_view_result)
                    binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_online)
                    binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_online_text))
                } else {
                    binding.tvOfflineBadge.setText(R.string.dashboard_status_waiting_result)
                    binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_processing)
                    binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_processing_text))
                }
            } else if (exam.isOfflineReady && exam.status.isRunningExamStatus()) {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_ready)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_ready)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_ready_text))
            } else if (exam.status.isRunningExamStatus()) {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_in_progress)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_processing)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_processing_text))
            } else if (exam.status.isEndedExamStatus()) {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_ended)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_processing)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_processing_text))
            } else {
                binding.tvOfflineBadge.setText(R.string.dashboard_status_online)
                binding.tvOfflineBadge.setBackgroundResource(R.drawable.bg_badge_online)
                binding.tvOfflineBadge.setTextColor(context.getColor(R.color.status_online_text))
            }
            binding.root.setOnClickListener { onClick(exam) }
        }

        private fun String.isRunningExamStatus(): Boolean {
            val normalized = trim().replace('-', '_').uppercase(Locale.US)
            return normalized == "ACTIVE" ||
                normalized == "OPEN" ||
                normalized == "IN_PROGRESS" ||
                normalized == "RUNNING"
        }

        private fun String.isEndedExamStatus(): Boolean {
            val normalized = trim().replace('-', '_').uppercase(Locale.US)
            return normalized == "END" ||
                normalized == "ENDED" ||
                normalized == "EXPIRED" ||
                normalized == "CLOSED"
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
