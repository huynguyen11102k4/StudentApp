package com.examhub.student.ui.appeals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.FragmentAppealDetailBinding
import com.examhub.student.util.extension.toLocalDisplayDateTime
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.toFriendlyAppealStatus
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppealDetailFragment : Fragment() {

    private var _binding: FragmentAppealDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppealDetailViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppealDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnBackToAppeals.setOnClickListener { findNavController().navigateUp() }

        collectOnStarted {
            launch { viewModel.appeal.collect { appeal -> appeal?.let(::bindAppeal) } }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.message.collect { message ->
                    Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_LONG).show()
                }
            }
        }
        viewModel.loadAppeal(
            appealId = arguments?.getString("appealId").orEmpty(),
            fallbackStudentName = arguments?.getString("studentName").orEmpty(),
            fallbackStudentCode = arguments?.getString("studentCode").orEmpty()
        )
    }

    private fun bindAppeal(appeal: Appeal) {
        binding.tvStudentName.text = appeal.studentName.ifBlank { getString(R.string.appeal_detail_student_unknown) }
        binding.tvStudentCode.text = getString(
            R.string.result_detail_student_code_format,
            appeal.studentCode.ifBlank { getString(R.string.appeal_detail_student_code_unknown) }
        )
        binding.tvExamName.text = appeal.examName.ifBlank { getString(R.string.result_detail_default_title) }
        binding.tvSubject.text = appeal.subject.takeIf { it.isNotBlank() }?.let { getString(R.string.appeal_detail_subject_format, it) }.orEmpty()
        binding.tvOldScore.text = appeal.newScore?.let {
            getString(R.string.appeal_score_changed_format, appeal.oldScore, it)
        } ?: getString(R.string.appeal_score_format, appeal.oldScore)
        binding.tvReason.text = appeal.reason.ifBlank { getString(R.string.appeal_detail_reason_empty) }
        binding.tvCreatedAt.text = getString(
            R.string.appeal_detail_created_at_format,
            appeal.createdAt.toLocalDisplayDateTime(appeal.createdAt)
        )
        binding.tvDetectedInfo.text = appeal.teacherNote.orEmpty()
        binding.chipStatus.text = appeal.status.toFriendlyAppealStatus()
        binding.cardAppealItems.visibility = if (appeal.teacherNote.isNullOrBlank()) View.GONE else View.VISIBLE

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
