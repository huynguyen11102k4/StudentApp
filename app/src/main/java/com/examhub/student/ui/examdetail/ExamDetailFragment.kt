package com.examhub.student.ui.examdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentExamDetailBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.showShimmer
import com.examhub.student.util.extension.replaceTechnicalLabels
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExamDetailFragment : Fragment() {

    private var _binding: FragmentExamDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamDetailViewModel by viewModel()
    private val examId: String get() = arguments?.getString("examId").orEmpty()
    private val openedFromNotification: Boolean get() = arguments?.getBoolean("openedFromNotification") == true
    private var hasExamContent = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExamDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnStartScanning.isEnabled = false
        binding.btnStartScanning.setOnClickListener {
            val isDownloading = viewModel.isDownloading.value

            if (isDownloading) {
                Snackbar.make(binding.root, R.string.exam_detail_start_locked, Snackbar.LENGTH_SHORT).show()
            } else if (!viewModel.canStartExam.value) {
                Snackbar.make(binding.root, R.string.exam_start_inactive, Snackbar.LENGTH_SHORT).show()
            } else {
                val bundle = Bundle().apply { putString("examId", examId) }
                findNavController().navigate(R.id.action_exam_detail_to_exam_start, bundle)
            }
        }
        binding.btnDownloadOffline.setOnClickListener {
            viewModel.downloadGradingData()
        }
        binding.btnViewResult.setOnClickListener {
            val resultId = viewModel.resultId.value
            if (resultId.isBlank()) {
                Snackbar.make(binding.root, R.string.exam_detail_waiting_result, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().navigate(
                R.id.resultDetailFragment,
                Bundle().apply {
                    putString("sheetId", resultId)
                    putString("examStatus", viewModel.status.value)
                }
            )
        }
        binding.cardPendingAppeals.setOnClickListener {
            val bundle = Bundle().apply { putString("examId", examId) }
            findNavController().navigate(R.id.action_exam_detail_to_appeals_list, bundle)
        }

        observeViewModel()
        viewModel.loadExam(examId, openedFromNotification)
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.examName.collect {
                    binding.tvExamName.text = it
                    if (it.isNotBlank()) {
                        hasExamContent = true
                        updateLoadingState(viewModel.isLoading.value)
                    }
                }
            }
            launch { viewModel.subject.collect { binding.tvSubject.text = it } }
            launch { viewModel.duration.collect { binding.tvDuration.text = getString(R.string.exam_detail_duration_format, it) } }
            launch { viewModel.questionCount.collect { binding.tvQuestionCount.text = getString(R.string.exam_detail_question_count_format, it) } }
            launch { viewModel.status.collect { binding.tvStatus.text = it } }
            launch { viewModel.examType.collect { binding.tvExamType.text = it } }
            launch {
                viewModel.gradingType.collect {
                    binding.tvGradingType.text = it
                }
            }
            launch { viewModel.templateName.collect { binding.tvTemplateName.text = it } }
            launch {
                viewModel.examWindowNotice.collect { notice ->
                    binding.tvExamWindowNotice.text = notice
                    binding.tvExamWindowNotice.visibility = if (notice.isBlank()) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.pendingAppealCount.collect { count ->
                    binding.tvPendingAppeals.text = getString(R.string.appeal_detail_pending_count, count)
                    binding.cardPendingAppeals.visibility = if (count > 0) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.isDownloading.collect { downloading ->
                    binding.btnDownloadOffline.isEnabled = !downloading
                    binding.btnStartScanning.isEnabled = viewModel.canStartExam.value && !downloading && !viewModel.isStartingSession.value
                    binding.progressDownload.visibility = if (downloading) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.isStartingSession.collect { starting ->
                    binding.btnStartScanning.isEnabled = viewModel.canStartExam.value && !starting && !viewModel.isDownloading.value
                    binding.btnStartScanning.text = getString(
                        if (starting) R.string.exam_detail_starting else R.string.exam_detail_start_scanning
                    )
                    if (starting) binding.btnStartScanning.alpha = 0.7f
                }
            }
            launch {
                viewModel.canStartExam.collect { canStart ->
                    binding.btnStartScanning.isEnabled = canStart && !viewModel.isDownloading.value && !viewModel.isStartingSession.value
                    binding.btnStartScanning.alpha = if (canStart) 1.0f else 0.55f
                }
            }
            launch {
                viewModel.resultOnly.collect { resultOnly ->
                    updateButtonVisibilities(resultOnly = resultOnly)
                }
            }
            launch {
                viewModel.canViewResult.collect { canViewResult ->
                    binding.btnViewResult.visibility = if (canViewResult) View.VISIBLE else View.GONE
                    updateButtonVisibilities(canViewResult = canViewResult)
                }
            }
            launch {
                viewModel.downloadStep.collect { step ->
                    if (step.isNotEmpty()) {
                        binding.tvDownloadStatus.text = step
                        binding.tvDownloadStatus.visibility = View.VISIBLE
                    } else {
                        binding.tvDownloadStatus.visibility = View.GONE
                    }
                }
            }
            launch {
                viewModel.isOfflineReady.collect { ready ->
                    if (ready) {
                        binding.btnDownloadOffline.text = getString(R.string.exam_detail_offline_ready)
                        binding.btnDownloadOffline.icon = null
                        binding.btnStartScanning.alpha = if (viewModel.canStartExam.value) 1.0f else 0.55f
                    } else {
                        binding.btnDownloadOffline.text = getString(R.string.exam_detail_download_template)
                        binding.btnStartScanning.alpha = if (viewModel.canStartExam.value) 1.0f else 0.55f
                    }
                    updateButtonVisibilities(isOfflineReady = ready)
                }
            }
            launch {
                viewModel.isExamExpired.collect { expired ->
                    updateButtonVisibilities(isExamExpired = expired)
                }
            }
            launch {
                viewModel.toastMessage.collect {
                    Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    updateLoadingState(loading)
                }
            }
            launch {
                viewModel.sessionStarted.collect { event ->
                    val session = event.session
                    val bundle = Bundle().apply {
                        putString("examId", examId)
                        putString("sessionId", session.sessionId)
                        putInt("remainingSeconds", event.remainingSeconds)
                        putInt("questionCount", event.questionCount)
                        putBoolean("isLockedMode", session.isLockedMode)
                        putString("classCode", event.omrCodes.classCode)
                        putString("studentCode", event.omrCodes.studentCode)
                        putString("studentCodeMode", event.omrCodes.studentCodeMode)
                    }
                    if (session.isLockedMode) {
                        findNavController().navigate(R.id.lockModeFragment, bundle)
                    } else {
                        findNavController().navigate(R.id.action_exam_detail_to_camera_ar, bundle)
                    }
                }
            }
            launch {
                viewModel.openResult.collect { resultId ->
                    findNavController().navigate(
                        R.id.resultDetailFragment,
                        Bundle().apply {
                            putString("sheetId", resultId)
                            putString("examStatus", viewModel.status.value)
                        }
                    )
                }
            }
        }
    }

    private fun updateButtonVisibilities(
        resultOnly: Boolean = viewModel.resultOnly.value,
        isOfflineReady: Boolean = viewModel.isOfflineReady.value,
        isExamExpired: Boolean = viewModel.isExamExpired.value,
        canViewResult: Boolean = viewModel.canViewResult.value
    ) {
        val hideStartScanning = resultOnly || isExamExpired || canViewResult
        val hideOfflineAlert = resultOnly || isOfflineReady || isExamExpired || canViewResult

        binding.btnStartScanning.visibility = if (hideStartScanning) View.GONE else View.VISIBLE
        binding.cardOfflineAlert.visibility = if (hideOfflineAlert) View.GONE else View.VISIBLE
    }

    private fun updateLoadingState(loading: Boolean) {
        val showSkeleton = loading && !hasExamContent
        binding.loadingSkeleton.root.showShimmer(showSkeleton)
        binding.scrollContent.visibility = if (showSkeleton) View.GONE else View.VISIBLE
        binding.progressBar.visibility = if (loading && !showSkeleton) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
