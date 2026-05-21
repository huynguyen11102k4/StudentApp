package com.examhub.student.ui.appeals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.ItemAppealBinding

class AppealAdapter(
    private val onItemClick: (Appeal) -> Unit
) : ListAdapter<Appeal, AppealAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAppealBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appeal: Appeal, onClick: (Appeal) -> Unit) {
            binding.tvStudentName.text = appeal.studentName
            binding.tvStudentCode.text = "Mã HS: ${appeal.studentCode}"
            binding.tvExamName.text = "${appeal.examName} — ${appeal.subject}"
            binding.tvReason.text = appeal.reason
            binding.tvScore.text = "${appeal.oldScore}đ"
            binding.tvCreatedAt.text = appeal.createdAt
            binding.chipStatus.text = when (appeal.status) {
                "PENDING" -> "Chờ xử lý"
                "RESOLVED" -> "Đã xử lý"
                "REJECTED" -> "Từ chối"
                else -> appeal.status
            }
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
