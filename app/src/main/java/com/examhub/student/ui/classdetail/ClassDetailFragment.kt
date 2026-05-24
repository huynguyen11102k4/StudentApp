package com.examhub.student.ui.classdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
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
        binding.tvTeacher.text = listOf(
            info?.teacher?.fullName.orEmpty().ifBlank { "Chưa cập nhật" },
            info?.teacher?.email.orEmpty()
        ).filter { it.isNotBlank() }.joinToString("\n")
        binding.tvStatus.text = listOfNotNull(
            (item.status ?: info?.status)?.takeIf { it.isNotBlank() },
            item.joinedAt?.takeIf { it.isNotBlank() }?.let { "Tham gia: $it" },
            item.approvedAt?.takeIf { it.isNotBlank() }?.let { "Duyệt: $it" }
        ).joinToString("\n").ifBlank { "Chưa cập nhật" }
        binding.tvStudentCount.text = (info?.studentCount
            ?: item.studentCount
            ?: info?.count?.students
            ?: item.count?.students
            ?: info?.count?.studentClasses
            ?: info?.count?.studentClassesCamel
            ?: item.count?.studentClasses
            ?: item.count?.studentClassesCamel
            ?: 0).toString()
        binding.tvExamCount.text = (info?.examCount
            ?: item.examCount
            ?: info?.count?.exams
            ?: item.count?.exams
            ?: 0).toString()
        binding.tvJoinCode.text = buildList {
            info?.classCode?.takeIf { it.isNotBlank() }?.let { add("Mã lớp: $it") }
            info?.joinCode?.takeIf { it.isNotBlank() }?.let { add("Mã tham gia: $it") }
            info?.approvalMode?.takeIf { it.isNotBlank() }?.let { add("Cách duyệt: $it") }
            item.internalId?.takeIf { it.isNotBlank() }?.let { add("Mã nội bộ của bạn: $it") }
            item.studentId?.takeIf { it.isNotBlank() }?.let { add("Student ID: $it") }
        }.joinToString("\n")
        binding.tvJoinCode.visibility = if (binding.tvJoinCode.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
