package com.examhub.student.ui.classdetail

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.ui.dashboard.RecentExamAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        binding.btnRequestLeave.setOnClickListener { showLeaveRequestConfirmation() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setExamSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        collectOnStarted {
            launch { viewModel.classDetail.collect { it?.let(::bindClass) } }
            launch { viewModel.classExams.collect(::bindClassExams) }
            launch { viewModel.classExamCount.collect { binding.tvExamCount.text = it.toString() } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_LONG).show() } }
            launch {
                viewModel.leaveRequested.collect {
                    Snackbar.make(binding.root, R.string.class_detail_leave_request_success, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        viewModel.load(arguments?.getString("classId").orEmpty())
    }

    private fun bindClass(item: MobileClassResponse) {
        val info = item.classInfo
        binding.tvClassName.text = info?.className.orEmpty().ifBlank { getString(R.string.common_not_updated) }
        binding.tvSubject.text = listOfNotNull(info?.subject, info?.grade, info?.schoolYear)
            .filter { it.isNotBlank() }
            .joinToString(binding.root.context.getString(R.string.common_separator_dot))
            .ifBlank { getString(R.string.class_detail_subject_empty) }
        binding.tvDescription.text = info?.description.orEmpty().ifBlank {
            getString(R.string.class_detail_description_empty)
        }
        binding.tvTeacher.text = listOf(info?.teacher?.fullName, info?.teacher?.email)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .joinToString("\n")
            .ifBlank { getString(R.string.common_not_updated) }
        binding.tvClassCode.text = info?.classCode.orEmpty().ifBlank {
            getString(R.string.common_not_updated)
        }
        binding.tvJoinCode.text = info?.joinCode.orEmpty().ifBlank {
            getString(R.string.common_not_updated)
        }
        binding.tvStudentCount.text = studentCount(item).toString()
        val leaveRequested = item.status == "LEAVE_REQUESTED"
        binding.tvLeaveRequestedStatus.visibility = if (leaveRequested) View.VISIBLE else View.GONE
        binding.btnRequestLeave.isEnabled = !leaveRequested
        binding.btnRequestLeave.text = getString(
            if (leaveRequested) R.string.class_detail_leave_request_pending_action
            else R.string.class_detail_leave_request_action
        )
    }

    private fun showLeaveRequestConfirmation() {
        if (viewModel.classDetail.value?.status == "LEAVE_REQUESTED") return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.class_detail_leave_request_title)
            .setMessage(R.string.class_detail_leave_request_confirm)
            .setNegativeButton(R.string.common_cancel, null)
            .setPositiveButton(R.string.class_detail_leave_request_confirm_action) { _, _ ->
                viewModel.requestLeaveClass()
            }
            .show()
    }

    private fun bindClassExams(exams: List<Exam>) {
        examAdapter.submitList(exams)
        binding.rvClassExams.visibility = if (exams.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyExams.visibility = if (exams.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openExam(exam: Exam) {
        if (exam.canViewResult && !exam.resultSheetId.isNullOrBlank()) {
            val resultId = exam.resultSheetId
            findNavController().navigate(
                R.id.resultDetailFragment,
                Bundle().apply {
                    putString("sheetId", resultId)
                    putString("examStatus", exam.status)
                }
            )
            return
        }
        if (exam.hasSubmitted || exam.resultOnly) {
            findNavController().navigate(R.id.resultsListFragment)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
