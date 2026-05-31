package com.examhub.student.ui.results

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.databinding.ItemResultBinding
import com.examhub.student.model.response.result.StudentResultSummaryResponse
import com.examhub.student.R
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ResultsAdapter(
    private val onClick: (StudentResultSummaryResponse) -> Unit
) : ListAdapter<StudentResultSummaryResponse, ResultsAdapter.ResultViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        return ResultViewHolder(ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(getItem(position))
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
            ).joinToString(" • ")
            binding.tvScore.text = result.totalScore?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
            binding.tvStatus.text = formatDate(result.gradedAt ?: result.createdAt)
            binding.root.setOnClickListener { onClick(result) }
        }

        private fun formatDate(value: String?): String {
            if (value.isNullOrBlank()) return ""
            val output = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
            val parsers = listOf(
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
            return parsers.firstNotNullOfOrNull { parser ->
                runCatching { parser.parse(value)?.let(output::format) }.getOrNull()
            } ?: value
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<StudentResultSummaryResponse>() {
        override fun areItemsTheSame(oldItem: StudentResultSummaryResponse, newItem: StudentResultSummaryResponse): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: StudentResultSummaryResponse, newItem: StudentResultSummaryResponse): Boolean =
            oldItem == newItem
    }
}
