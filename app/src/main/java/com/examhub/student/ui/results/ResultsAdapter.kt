package com.examhub.student.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.paging.PagingDataAdapter
import com.examhub.student.databinding.ItemResultBinding
import com.examhub.student.model.response.result.StudentResultSummaryResponse
import com.examhub.student.R
import com.examhub.student.util.extension.toLocalDisplayDateTime
import java.util.Locale

class ResultsAdapter(
    private val onClick: (StudentResultSummaryResponse) -> Unit
) : PagingDataAdapter<StudentResultSummaryResponse, ResultsAdapter.ResultViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        return ResultViewHolder(ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    class ResultViewHolder(
        private val binding: ItemResultBinding,
        private val onClick: (StudentResultSummaryResponse) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: StudentResultSummaryResponse) {
            binding.tvExamName.text = result.exam?.name ?: binding.root.context.getString(R.string.result_item_default_exam)
            binding.tvSubject.text = listOfNotNull(
                result.exam?.subject?.takeIf { it.isNotBlank() },
                result.source?.takeIf { it.isNotBlank() }
            ).joinToString(binding.root.context.getString(R.string.common_separator_dot))
            binding.tvScore.text = result.totalScore?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
            binding.tvStatus.text = (result.gradedAt ?: result.createdAt).toLocalDisplayDateTime()
            binding.root.setOnClickListener { onClick(result) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudentResultSummaryResponse>() {
        override fun areItemsTheSame(oldItem: StudentResultSummaryResponse, newItem: StudentResultSummaryResponse): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: StudentResultSummaryResponse, newItem: StudentResultSummaryResponse): Boolean =
            oldItem == newItem
    }
}
