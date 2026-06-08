package com.examhub.student.ui.appeals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagingDataAdapter
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.ItemAppealBinding
import com.examhub.student.util.extension.toLocalDisplayDateTime
import com.examhub.student.util.extension.toFriendlyAppealStatus

class AppealAdapter(
    private val onItemClick: (Appeal) -> Unit
) : PagingDataAdapter<Appeal, AppealAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAppealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appeal: Appeal, onClick: (Appeal) -> Unit) {
            val context = binding.root.context
            binding.tvStudentName.text = appeal.studentName.ifBlank {
                appeal.studentCode.ifBlank { context.getString(R.string.appeal_detail_student_unknown) }
            }
            binding.tvStudentCode.text = context.getString(
                R.string.result_detail_student_code_format,
                appeal.studentCode.ifBlank { context.getString(R.string.appeal_detail_student_code_unknown) }
            )
            binding.tvExamName.text = listOf(appeal.examName, appeal.subject)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            binding.tvReason.text = appeal.reason
            binding.tvScore.text = appeal.newScore?.let {
                context.getString(R.string.appeal_score_changed_format, appeal.oldScore, it)
            } ?: context.getString(R.string.appeal_score_format, appeal.oldScore)
            binding.tvCreatedAt.text = appeal.createdAt.toLocalDisplayDateTime(appeal.createdAt)
            binding.chipStatus.text = appeal.status.toFriendlyAppealStatus()
            binding.root.setOnClickListener { onClick(appeal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onItemClick) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Appeal>() {
        override fun areItemsTheSame(oldItem: Appeal, newItem: Appeal) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Appeal, newItem: Appeal) = oldItem == newItem
    }
}
