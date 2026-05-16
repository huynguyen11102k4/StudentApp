package com.omr.scanner.student.ui.examstart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.omr.scanner.student.R
import com.omr.scanner.student.databinding.FragmentExamStartBinding
import com.omr.scanner.student.model.response.MobileExamDetailResponse
import com.omr.scanner.student.ui.applySystemWindowInsets
import com.omr.scanner.student.ui.collectOnStarted
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
            launch { viewModel.sessionStarted.collect { session ->
                val bundle = Bundle().apply {
                    putString("examId", examId)
                    putString("sessionId", session.sessionId)
                    putInt("remainingSeconds", session.remainingSeconds)
                    putBoolean("isLockedMode", session.isLockedMode)
                }
                findNavController().navigate(R.id.action_exam_start_to_lock_mode, bundle)
            } }
        }

        viewModel.load(examId)
    }

    private fun bindExam(exam: MobileExamDetailResponse) {
        binding.tvExamName.text = exam.name
        binding.tvSubject.text = exam.subject
        binding.tvDuration.text = getString(R.string.exam_start_duration_format, exam.duration)
        binding.tvQuestionCount.text = getString(R.string.exam_start_question_count_format, exam.totalQuestions)
        binding.tvMode.text = exam.examType
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
