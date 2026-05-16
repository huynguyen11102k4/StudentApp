package com.omr.scanner.student.ui.classlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.omr.scanner.student.R
import com.omr.scanner.student.data.model.SchoolClass
import com.omr.scanner.student.databinding.ItemClassBinding

class ClassListAdapter(
    private val onItemClick: (SchoolClass) -> Unit,
    private val onCopyCode: (String) -> Unit
) : ListAdapter<SchoolClass, ClassListAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(private val binding: ItemClassBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schoolClass: SchoolClass, onItemClick: (SchoolClass) -> Unit, onCopyCode: (String) -> Unit) {
            binding.tvClassName.text = schoolClass.name
            binding.tvSubject.text = schoolClass.subject
            binding.tvStudentCount.text = binding.root.context.getString(R.string.class_list_student_count_short, schoolClass.studentCount)
            binding.btnCopyCode.text = binding.root.context.getString(R.string.class_list_join_code_format, schoolClass.joinCode)
            binding.root.setOnClickListener { onItemClick(schoolClass) }
            binding.btnCopyCode.setOnClickListener { onCopyCode(schoolClass.joinCode) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClassBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onCopyCode)
    }

    class DiffCallback : DiffUtil.ItemCallback<SchoolClass>() {
        override fun areItemsTheSame(oldItem: SchoolClass, newItem: SchoolClass) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SchoolClass, newItem: SchoolClass) = oldItem == newItem
    }
}
