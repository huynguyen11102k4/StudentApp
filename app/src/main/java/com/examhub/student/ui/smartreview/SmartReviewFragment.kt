package com.examhub.student.ui.smartreview

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSmartReviewBinding
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.ui.lockmode.LockFlowMonitorController
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.helper.protectScreenFromCapture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class SmartReviewFragment : Fragment() {

    private var _binding: FragmentSmartReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SmartReviewViewModel by viewModel()
    private val lockModeRepository: LockModeRepository by inject()
    private var sessionTimeoutJob: Job? = null
    private var lockFlowMonitor: LockFlowMonitorController? = null
    private var finishingExpiredSession = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSmartReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        protectScreenFromCapture()
        enterKioskModeIfLocked()
        setupLockFlowMonitor()
        binding.topBar.applySystemWindowInsets(top = true)
        binding.topBar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnRetake.setOnClickListener { openCameraForCurrentExam() }
        binding.btnContinueCapture.setOnClickListener {
            showSubmitConfirmDialog()
        }

        collectOnStarted {
            launch {
                viewModel.reviewState.collect { state ->
                    bindReviewState(state)
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    binding.btnRetake.isEnabled = !loading
                    binding.btnContinueCapture.isEnabled = !loading
                }
            }
            launch {
                viewModel.savedSuccess.collect { submission ->
                    val response = submission.response ?: return@collect
                    Snackbar.make(binding.root, R.string.smart_review_submitted, Snackbar.LENGTH_SHORT).show()
                    val bundle = Bundle().apply {
                        putString("examId", viewModel.reviewState.value.examId)
                        putString("sheetId", response.resultId.orEmpty())
                        putString("submissionId", response.submissionId)
                        putString("clientSubmissionId", submission.clientSubmissionId)
                    }
                    findNavController().navigate(R.id.action_smart_review_to_submission_end, bundle)
                }
            }
            launch {
                viewModel.blankSubmissionFinished.collect { submission ->
                    navigateToSubmissionEnd(
                        resultId = submission.response?.resultId,
                        clientSubmissionId = submission.clientSubmissionId
                    )
                }
            }
            launch {
                viewModel.submissionFrozen.collect { frozen ->
                    navigateToSubmissionEnd(
                        resultId = null,
                        clientSubmissionId = frozen.clientSubmissionId
                    )
                }
            }
            launch {
                viewModel.errorMessage.collect { message ->
                    Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_LONG).show()
                    if (finishingExpiredSession) finishExpiredSession()
                }
            }
        }

        viewModel.loadReviewData(arguments?.getString("submissionId").orEmpty())
        viewModel.setFallbackSession(arguments?.getString("sessionId").orEmpty())
        startSessionTimeout()
    }

    override fun onResume() {
        super.onResume()
        enterKioskModeIfLocked()
    }

    private fun enterKioskModeIfLocked() {
        if (arguments?.getString("sessionId").isNullOrBlank()) return
        (requireActivity() as? MainActivity)?.enterKioskMode()
    }

    private fun setupLockFlowMonitor() {
        val sessionId = arguments?.getString("sessionId").orEmpty()
        if (sessionId.isBlank()) return
        lockFlowMonitor = LockFlowMonitorController(
            context = requireContext(),
            lockModeRepository = lockModeRepository,
            scope = viewLifecycleOwner.lifecycleScope,
            sessionIdProvider = { viewModel.reviewState.value.sessionId.ifBlank { arguments?.getString("sessionId").orEmpty() } },
            screenName = "smart_review"
        ).also { it.start() }
    }

    private fun openCameraForCurrentExam() {
        val examId = viewModel.reviewState.value.examId.ifBlank {
            arguments?.getString("examId").orEmpty()
        }
        if (examId.isBlank()) {
            Snackbar.make(binding.root, R.string.smart_review_missing_exam, Snackbar.LENGTH_LONG).show()
            return
        }

        val navController = findNavController()
        val returnedToCamera = navController.popBackStack(R.id.cameraARFragment, false)
        if (!returnedToCamera) {
            val bundle = Bundle().apply {
                putString("examId", examId)
                putString("sessionId", viewModel.reviewState.value.sessionId)
                putInt("remainingSeconds", remainingSeconds())
                putInt("questionCount", arguments?.getInt("questionCount") ?: 0)
                putLong("timerStartedAt", System.currentTimeMillis())
            }
            navController.navigate(R.id.action_smart_review_to_camera_ar, bundle)
        }
    }

    private fun showSubmitConfirmDialog() {
        val state = viewModel.reviewState.value
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.smart_review_submit_confirm_title)
            .setMessage(R.string.smart_review_submit_confirm_message)
            .setNegativeButton(R.string.smart_review_submit_confirm_review, null)
            .setPositiveButton(R.string.smart_review_submit_confirm_submit) { _, _ ->
                viewModel.saveResults(state.score ?: 0.0, state.examId, state.studentId)
            }
            .show()
    }

    private fun startSessionTimeout() {
        val seconds = remainingSeconds()
        if (seconds <= 0) {
            if (!arguments?.getString("sessionId").isNullOrBlank()) {
                handleSessionExpired()
            }
            return
        }
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(seconds * 1_000L)
            handleSessionExpired()
        }
    }

    private fun handleSessionExpired() {
        if (_binding == null || finishingExpiredSession) return
        finishingExpiredSession = true
        val state = viewModel.reviewState.value
        if (state.hasOmrResult && !viewModel.isLoading.value) {
            Snackbar.make(binding.root, R.string.lock_mode_time_expired_auto_submit, Snackbar.LENGTH_LONG).show()
            viewModel.saveResults(state.score ?: 0.0, state.examId, state.studentId)
        } else {
            viewModel.submitBlankOnTimeout(
                examId = state.examId.ifBlank { arguments?.getString("examId").orEmpty() },
                questionCount = arguments?.getInt("questionCount") ?: 0
            )
        }
    }

    private fun finishExpiredSession() {
        if (_binding == null) return
        (requireActivity() as? MainActivity)?.exitKioskMode()
        navigateToSubmissionEnd(null)
    }

    private fun navigateToSubmissionEnd(resultId: String?, clientSubmissionId: String = "") {
        if (_binding == null) return
        (requireActivity() as? MainActivity)?.exitKioskMode()
        findNavController().navigate(
            R.id.submissionEndFragment,
            Bundle().apply {
                putString("examId", viewModel.reviewState.value.examId.ifBlank { arguments?.getString("examId").orEmpty() })
                putString("sheetId", resultId.orEmpty())
                putString("submissionId", arguments?.getString("submissionId").orEmpty())
                putString("clientSubmissionId", clientSubmissionId)
            },
            navOptions {
                popUpTo(R.id.lockModeFragment) { inclusive = true }
            }
        )
    }

    private fun remainingSeconds(): Int {
        val initial = arguments?.getInt("remainingSeconds") ?: 0
        val startedAt = arguments?.getLong("timerStartedAt") ?: 0L
        return viewModel.currentRemainingSeconds(
            examId = arguments?.getString("examId").orEmpty(),
            fallbackInitial = initial,
            fallbackStartedAt = startedAt
        )
    }

    private fun bindReviewState(state: ReviewUiState) {
        val emptyValue = getString(R.string.smart_review_null_value)
        binding.tvScore.text = getString(R.string.smart_review_detection_title)
        binding.tvStudentName.text = getString(R.string.smart_review_student_name_format, state.studentName ?: emptyValue)
        binding.tvStudentCode.text = getString(R.string.smart_review_student_code_format, state.studentId.ifBlank { emptyValue })
        binding.tvClassCode.text = getString(R.string.smart_review_class_code_format, state.classCode ?: emptyValue)
        binding.tvExamCode.text = getString(R.string.smart_review_exam_code_format, state.examCode ?: emptyValue)
        binding.omrWarningBanner.visibility = if (state.hasOmrWarning) View.VISIBLE else View.GONE
        bindDebugImage(state.debugImageBase64)
    }

    private fun bindDebugImage(base64: String) {
        if (base64.isBlank()) {
            binding.ivDebugImage.setImageDrawable(null)
            binding.ivDebugImage.visibility = View.GONE
            binding.tvNoDebugImage.visibility = View.VISIBLE
            return
        }

        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onSuccess { bitmap ->
            if (bitmap == null) {
                binding.ivDebugImage.setImageDrawable(null)
                binding.ivDebugImage.visibility = View.GONE
                binding.tvNoDebugImage.visibility = View.VISIBLE
            } else {
                binding.ivDebugImage.setImageBitmap(bitmap)
                binding.ivDebugImage.visibility = View.VISIBLE
                binding.tvNoDebugImage.visibility = View.GONE
            }
        }.onFailure {
            binding.ivDebugImage.setImageDrawable(null)
            binding.ivDebugImage.visibility = View.GONE
            binding.tvNoDebugImage.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionTimeoutJob?.cancel()
        lockFlowMonitor?.stop()
        lockFlowMonitor = null
        _binding = null
    }
}
