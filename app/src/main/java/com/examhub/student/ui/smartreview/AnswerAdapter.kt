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
            val display = when (answer.status) {
                "correct" -> "HS: ${answer.studentAnswer ?: "---"} → ĐA: ${answer.correctAnswer}"
                "wrong" -> "HS: ${answer.studentAnswer ?: "---"} → ĐA: ${answer.correctAnswer}"
                "empty" -> "HS: --- → ĐA: ${answer.correctAnswer}"
                else -> "HS: ${answer.studentAnswer ?: "---"} → ĐA: ${answer.correctAnswer}"
            }
            binding.tvStudentAnswer.text = display
            binding.ivStatusIcon.visibility = android.view.View.VISIBLE
            binding.ivStatusIcon.setImageResource(
                when (answer.status) {
                    "correct" -> com.examhub.student.R.drawable.ic_check
                    "wrong" -> com.examhub.student.R.drawable.ic_close
                    else -> com.examhub.student.R.drawable.ic_check
                }
            )
            binding.ivStatusIcon.setColorFilter(
                when (answer.status) {
                    "correct" -> android.graphics.Color.parseColor("#4CAF50")
                    "wrong" -> android.graphics.Color.parseColor("#F44336")
                    else -> android.graphics.Color.parseColor("#FFC107")
                }
            )
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
