package com.examhub.student.ui.examlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import com.examhub.student.data.model.Exam
import com.examhub.student.databinding.ItemExamBinding
import com.examhub.student.ui.dashboard.RecentExamAdapter

class ExamPagingAdapter(
    private val onItemClick: (Exam) -> Unit
) : PagingDataAdapter<Exam, RecentExamAdapter.ViewHolder>(RecentExamAdapter.DiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentExamAdapter.ViewHolder =
        RecentExamAdapter.ViewHolder(ItemExamBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecentExamAdapter.ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onItemClick) }
    }
}
