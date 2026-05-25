package com.examhub.student.ui.examstart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentExamStartBinding
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import com.examhub.student.extension.toFriendlyExamStatus
import com.examhub.student.extension.toFriendlyGradingType
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExamStartFragment : Fragment() {
    private var _binding: FragmentExamStartBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamStartViewModel by viewModel()
    private val examId: String get() = arguments?.getString("examId").orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExamStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnStart.setOnClickListener { showStartConfirmDialog() }

        collectOnStarted {
            launch { viewModel.exam.collect { it?.let(::bindExam) } }
            launch { viewModel.isLoading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnStart.isEnabled = !loading
            } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
            launch { viewModel.sessionStarted.collect { event ->
                val session = event.session
                val bundle = Bundle().apply {
                    putString("examId", examId)
                    putString("sessionId", session.sessionId)
                    putInt("remainingSeconds", session.remainingSeconds)
                    putInt("questionCount", viewModel.exam.value?.totalQuestions ?: 0)
                    putBoolean("isLockedMode", session.isLockedMode)
                    putString("classCode", event.omrCodes.classCode)
                    putString("studentCode", event.omrCodes.studentCode)
                    putString("studentCodeMode", event.omrCodes.studentCodeMode)
                }
                findNavController().navigate(R.id.action_exam_start_to_lock_mode, bundle)
            } }
        }

        viewModel.load(examId)
    }

    private fun bindExam(exam: MobileExamDetailResponse) {
        binding.tvExamName.text = exam.name
        binding.tvSubject.text = listOfNotNull(
            exam.subject.takeIf { it.isNotBlank() },
            exam.classInfo?.className?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        binding.tvDuration.text = getString(R.string.exam_start_duration_format, exam.durationMinutes)
        binding.tvQuestionCount.text = getString(R.string.exam_start_question_count_format, exam.totalQuestions)
        binding.tvMode.text = listOf(
            exam.gradingType.toFriendlyGradingType().ifBlank { "Học sinh nộp bài" },
            if (exam.onlineConfig?.isLockedMode == true) "Lock mode" else "Không khóa màn hình",
            exam.status.toFriendlyExamStatus()
        ).joinToString(" • ")
    }

    private fun showStartConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.exam_start_confirm_title)
            .setMessage(R.string.exam_start_confirm_message)
            .setNegativeButton(R.string.exam_start_cancel, null)
            .setPositiveButton(R.string.exam_start_confirm) { _, _ -> viewModel.startSession() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
