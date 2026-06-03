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
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.toFriendlyExamStatus
import com.examhub.student.util.extension.toFriendlyGradingType
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
                binding.btnStart.isEnabled = !loading && viewModel.canStartExam.value
            } }
            launch { viewModel.canStartExam.collect { canStart ->
                binding.btnStart.isEnabled = canStart && !viewModel.isLoading.value
                binding.btnStart.alpha = if (canStart) 1.0f else 0.55f
            } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
            launch { viewModel.sessionStarted.collect { event ->
                val session = event.session
                val bundle = Bundle().apply {
                    putString("examId", examId)
                    putString("sessionId", session.sessionId)
                    putInt("remainingSeconds", event.remainingSeconds)
                    putInt("questionCount", viewModel.exam.value?.totalQuestions ?: 0)
                    putBoolean("isLockedMode", session.isLockedMode)
                    putString("classCode", event.omrCodes.classCode)
                    putString("studentCode", event.omrCodes.studentCode)
                    putString("studentCodeMode", event.omrCodes.studentCodeMode)
                }
                findNavController().navigate(R.id.action_exam_start_to_lock_mode, bundle)
            } }
            launch { viewModel.sessionResume.collect { event ->
                Snackbar.make(binding.root, R.string.exam_start_resume_local_session, Snackbar.LENGTH_SHORT).show()
                val bundle = Bundle().apply {
                    putString("examId", event.examId)
                    putString("sessionId", event.sessionId)
                    putInt("remainingSeconds", event.remainingSeconds)
                    putInt("questionCount", event.questionCount)
                    putBoolean("isLockedMode", event.isLockedMode)
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
        ).joinToString(binding.root.context.getString(R.string.common_separator_dot))
        binding.tvDuration.text = getString(R.string.exam_start_duration_format, exam.durationMinutes)
        binding.tvQuestionCount.text = getString(R.string.exam_start_question_count_format, exam.totalQuestions)
        binding.tvMode.text = listOf(
            exam.gradingType.toFriendlyGradingType().ifBlank { getString(R.string.exam_detail_default_grading_type) },
            if (exam.onlineConfig?.isLockedMode == true) getString(R.string.exam_start_lock_mode) else getString(R.string.exam_start_no_lock_mode),
            exam.status.toFriendlyExamStatus()
        ).joinToString(binding.root.context.getString(R.string.common_separator_dot))
    }

    private fun showStartConfirmDialog() {
        if (!viewModel.canStartExam.value) {
            Snackbar.make(binding.root, R.string.exam_start_inactive, Snackbar.LENGTH_SHORT).show()
            return
        }
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
