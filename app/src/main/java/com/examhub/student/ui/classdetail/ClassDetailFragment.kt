package com.examhub.student.ui.classdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentClassDetailStudentBinding
import com.examhub.student.model.response.MobileClassResponse
import com.examhub.student.ui.applySystemWindowInsets
import com.examhub.student.ui.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassDetailFragment : Fragment() {
    private var _binding: FragmentClassDetailStudentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassDetailViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassDetailStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        collectOnStarted {
            launch { viewModel.classDetail.collect { it?.let(::bindClass) } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
        }
        viewModel.load(arguments?.getString("classId").orEmpty())
    }

    private fun bindClass(item: MobileClassResponse) {
        val info = item.classInfo
        binding.tvClassName.text = info?.className.orEmpty()
        binding.tvSubject.text = info?.subject.orEmpty().ifBlank { "Chưa có môn học" }
        binding.tvDescription.text = info?.description.orEmpty().ifBlank { "Chưa có mô tả lớp học." }
        binding.tvGradeYear.text = listOf(info?.grade, info?.schoolYear)
            .filterNotNull()
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { "Chưa cập nhật khối / năm học" }
        binding.tvTeacher.text = info?.teacher?.user?.fullName.orEmpty()
            .ifBlank { info?.teacher?.user?.email.orEmpty() }
            .ifBlank { "Chưa cập nhật" }
        binding.tvStatus.text = item.status ?: info?.status.orEmpty()
        binding.tvJoinCode.text = info?.joinCode ?: item.internalId.orEmpty()
        binding.tvStudentCount.text = (info?.count?.classMembers
            ?: info?.count?.students
            ?: info?.count?.members
            ?: 0).toString()
        binding.tvExamCount.text = (info?.count?.exams ?: info?.count?.examAssignments ?: 0).toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
