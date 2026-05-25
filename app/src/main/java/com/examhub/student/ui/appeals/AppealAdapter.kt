package com.examhub.student.ui.appeals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.ItemAppealBinding
import com.examhub.student.extension.toFriendlyAppealStatus

class AppealAdapter(
    private val onItemClick: (Appeal) -> Unit
) : ListAdapter<Appeal, AppealAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAppealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appeal: Appeal, onClick: (Appeal) -> Unit) {
            val context = binding.root.context
            binding.tvStudentName.text = appeal.studentName.ifBlank { appeal.studentCode }
            binding.tvStudentCode.text = "Mã HS: ${appeal.studentCode.ifBlank { "Chưa xác định" }}"
            binding.tvExamName.text = listOf(appeal.examName, appeal.subject)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            binding.tvReason.text = appeal.reason
            binding.tvScore.text = appeal.newScore?.let {
                context.getString(R.string.appeal_score_changed_format, appeal.oldScore, it)
            } ?: context.getString(R.string.appeal_score_format, appeal.oldScore)
            binding.tvCreatedAt.text = appeal.createdAt.toDisplayDateTime()
            binding.chipStatus.text = appeal.status.toFriendlyAppealStatus()
            binding.root.setOnClickListener { onClick(appeal) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppealBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Appeal>() {
        override fun areItemsTheSame(oldItem: Appeal, newItem: Appeal) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Appeal, newItem: Appeal) = oldItem == newItem
    }
}

private fun String.toDisplayDateTime(): String {
    return replace("T", " ")
        .removeSuffix("Z")
        .substringBefore(".")
}
