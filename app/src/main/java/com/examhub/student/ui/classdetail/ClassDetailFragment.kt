package com.examhub.student.ui.classdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.databinding.FragmentClassDetailStudentBinding
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import com.examhub.student.ui.dashboard.RecentExamAdapter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassDetailFragment : Fragment() {
    private var _binding: FragmentClassDetailStudentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassDetailViewModel by viewModel()
    private lateinit var examAdapter: RecentExamAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassDetailStudentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        examAdapter = RecentExamAdapter(::openExam)
        binding.rvClassExams.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = examAdapter
            isNestedScrollingEnabled = false
        }

        collectOnStarted {
            launch { viewModel.classDetail.collect { it?.let(::bindClass) } }
            launch { viewModel.classExams.collect(::bindClassExams) }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
        }
        viewModel.load(arguments?.getString("classId").orEmpty())
    }

    private fun bindClass(item: MobileClassResponse) {
        val info = item.classInfo
        binding.tvClassName.text = info?.className.orEmpty().ifBlank { getString(R.string.common_not_updated) }
        binding.tvSubject.text = listOfNotNull(info?.subject, info?.grade, info?.schoolYear)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .ifBlank { getString(R.string.class_detail_subject_empty) }
        binding.tvDescription.text = info?.description.orEmpty().ifBlank {
            getString(R.string.class_detail_description_empty)
        }
        binding.tvTeacher.text = listOf(info?.teacher?.fullName, info?.teacher?.email)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString("\n")
            .ifBlank { getString(R.string.common_not_updated) }
        binding.tvClassCode.text = (info?.classCode ?: info?.joinCode).orEmpty().ifBlank {
            getString(R.string.common_not_updated)
        }
        binding.tvStudentCount.text = studentCount(item).toString()
        binding.tvExamCount.text = examCount(item).toString()
    }

    private fun bindClassExams(exams: List<Exam>) {
        examAdapter.submitList(exams)
        binding.rvClassExams.visibility = if (exams.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyExams.visibility = if (exams.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openExam(exam: Exam) {
        if (exam.hasSubmitted && !exam.resultSheetId.isNullOrBlank()) {
            findNavController().navigate(
                R.id.resultDetailFragment,
                Bundle().apply { putString("sheetId", exam.resultSheetId) }
            )
            return
        }
        findNavController().navigate(
            R.id.examDetailFragment,
            Bundle().apply { putString("examId", exam.id) }
        )
    }

    private fun studentCount(item: MobileClassResponse): Int {
        val info = item.classInfo
        return info?.studentCount
            ?: item.studentCount
            ?: info?.count?.students
            ?: item.count?.students
            ?: info?.count?.studentClasses
            ?: info?.count?.studentClassesCamel
            ?: item.count?.studentClasses
            ?: item.count?.studentClassesCamel
            ?: 0
    }

    private fun examCount(item: MobileClassResponse): Int {
        val info = item.classInfo
        return info?.examCount
            ?: item.examCount
            ?: info?.count?.exams
            ?: item.count?.exams
            ?: 0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
