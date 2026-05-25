package com.examhub.student.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.databinding.ItemResultAnswerBinding
import com.examhub.student.model.response.result.StudentResultAnswerResponse
import com.examhub.student.R

class ResultAnswerAdapter : ListAdapter<StudentResultAnswerResponse, ResultAnswerAdapter.AnswerViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnswerViewHolder {
        return AnswerViewHolder(ItemResultAnswerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: AnswerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AnswerViewHolder(private val binding: ItemResultAnswerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(answer: StudentResultAnswerResponse) {
            binding.tvQuestion.text = binding.root.context.getString(R.string.result_answer_question_format, answer.questionNumber)
            binding.tvStudentAnswer.text = answer.studentAnswer ?: "-"
            binding.tvCorrectAnswer.text = answer.correctAnswerText() ?: "-"
            val context = binding.root.context
            when (answer.isCorrect) {
                true -> {
                    binding.tvStatus.text = context.getString(R.string.result_answer_correct)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_answer_status_correct)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.on_success_container))
                }
                false -> {
                    binding.tvStatus.text = context.getString(R.string.result_answer_wrong)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_answer_status_wrong)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.on_error_container))
                }
                null -> {
                    binding.tvStatus.text = context.getString(R.string.result_answer_pending)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_answer_status_neutral)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudentResultAnswerResponse>() {
        override fun areItemsTheSame(oldItem: StudentResultAnswerResponse, newItem: StudentResultAnswerResponse): Boolean =
            oldItem.questionNumber == newItem.questionNumber

        override fun areContentsTheSame(oldItem: StudentResultAnswerResponse, newItem: StudentResultAnswerResponse): Boolean =
            oldItem == newItem
    }
}
