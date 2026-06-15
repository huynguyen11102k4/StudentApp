package com.examhub.student.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.ItemAppealBinding
import com.examhub.student.util.extension.toFriendlyAppealStatus
import com.examhub.student.util.extension.toLocalDisplayDateTime

class ResultAppealsAdapter(
    private val onItemClick: (Appeal) -> Unit
) : ListAdapter<Appeal, ResultAppealsAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAppealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appeal: Appeal, onItemClick: (Appeal) -> Unit) {
            val context = binding.root.context
            binding.tvStudentName.text = appeal.studentName.ifBlank {
                appeal.studentCode.ifBlank { context.getString(R.string.appeal_detail_student_unknown) }
            }
            binding.tvStudentCode.text = context.getString(
                R.string.result_detail_student_code_format,
                appeal.studentCode.ifBlank { context.getString(R.string.appeal_detail_student_code_unknown) }
            )
            binding.tvExamName.text = listOf(appeal.examName, appeal.subject)
                .filter(String::isNotBlank)
                .joinToString(" - ")
                .ifBlank { context.getString(R.string.result_detail_default_title) }
            binding.tvReason.text = appeal.reason.ifBlank {
                context.getString(R.string.appeal_detail_reason_empty)
            }
            binding.tvScore.text = appeal.newScore?.let {
                context.getString(R.string.appeal_score_changed_format, appeal.oldScore, it)
            } ?: context.getString(R.string.appeal_score_format, appeal.oldScore)
            binding.tvCreatedAt.text = appeal.createdAt.toLocalDisplayDateTime(appeal.createdAt)
            binding.chipStatus.text = appeal.status.toFriendlyAppealStatus()
            binding.root.setOnClickListener { onItemClick(appeal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAppealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    private class DiffCallback : DiffUtil.ItemCallback<Appeal>() {
        override fun areItemsTheSame(oldItem: Appeal, newItem: Appeal): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Appeal, newItem: Appeal): Boolean = oldItem == newItem
    }
}
