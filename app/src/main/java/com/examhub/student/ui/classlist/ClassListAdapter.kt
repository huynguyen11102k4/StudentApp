package com.examhub.student.ui.classlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.examhub.student.R
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.databinding.ItemClassBinding

class ClassListAdapter(
    private val onItemClick: (SchoolClass) -> Unit
) : PagingDataAdapter<SchoolClass, ClassListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemClassBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schoolClass: SchoolClass, onItemClick: (SchoolClass) -> Unit) {
            binding.tvClassName.text = schoolClass.name
            binding.tvSubject.text = schoolClass.subject
            binding.tvStudentCount.text = binding.root.context.getString(R.string.class_list_student_count_short, schoolClass.studentCount)
            binding.tvLeaveRequestedBadge.visibility =
                if (schoolClass.status == "LEAVE_REQUESTED") View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onItemClick(schoolClass) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClassBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it, onItemClick) }
    }

    class DiffCallback : DiffUtil.ItemCallback<SchoolClass>() {
        override fun areItemsTheSame(oldItem: SchoolClass, newItem: SchoolClass) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SchoolClass, newItem: SchoolClass) = oldItem == newItem
    }
}
