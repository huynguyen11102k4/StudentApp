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
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExamDetailFragment : Fragment() {

    private var _binding: FragmentExamDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamDetailViewModel by viewModel()
    private val examId: String get() = arguments?.getString("examId").orEmpty()

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
        binding.cardPendingAppeals.setOnClickListener {
            val bundle = Bundle().apply { putString("examId", examId) }
            findNavController().navigate(R.id.action_exam_detail_to_appeals_list, bundle)
        }

        observeViewModel()
        viewModel.loadExam(examId)
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch { viewModel.examName.collect { binding.tvExamName.text = it } }
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
                    binding.btnStartScanning.text = if (starting) "Đang bắt đầu..." else getString(R.string.exam_detail_start_scanning)
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
                    binding.cardOfflineAlert.visibility = if (ready) View.GONE else View.VISIBLE
                    binding.btnStartScanning.visibility = View.VISIBLE
                }
            }
            launch {
                viewModel.toastMessage.collect {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.sessionStarted.collect { session ->
                    val bundle = Bundle().apply {
                        putString("examId", examId)
                        putString("sessionId", session.sessionId)
                    }
                    findNavController().navigate(R.id.action_exam_detail_to_camera_ar, bundle)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
