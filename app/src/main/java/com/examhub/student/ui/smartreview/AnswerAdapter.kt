package com.examhub.student.ui.smartreview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.data.model.Answer
import com.examhub.student.databinding.ItemAnswerBinding

class AnswerAdapter(
    private val onEditClick: (Answer) -> Unit
) : ListAdapter<Answer, AnswerAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(answer: Answer, onEditClick: (Answer) -> Unit) {
            binding.tvQuestionNo.text = answer.questionNo.toString()
            binding.tvStudentAnswer.text = "HS: ${answer.studentAnswer ?: "---"}"
            binding.ivStatusIcon.visibility = android.view.View.GONE
            binding.btnEditAnswer.setOnClickListener { onEditClick(answer) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAnswerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onEditClick)
    }

    class DiffCallback : DiffUtil.ItemCallback<Answer>() {
        override fun areItemsTheSame(oldItem: Answer, newItem: Answer) = oldItem.questionNo == newItem.questionNo
        override fun areContentsTheSame(oldItem: Answer, newItem: Answer) = oldItem == newItem
    }
}
