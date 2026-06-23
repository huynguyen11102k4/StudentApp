package com.examhub.student.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.DialogCreateAppealBinding
import com.examhub.student.databinding.FragmentResultDetailBinding
import com.examhub.student.util.extension.toLocalDisplayDateTime
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ResultDetailFragment : Fragment() {
    private var _binding: FragmentResultDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultDetailViewModel by viewModel()
    private val sheetId: String get() = arguments?.getString("sheetId").orEmpty()
    private var currentResult: StudentResultDetailResponse? = null
    private lateinit var appealsAdapter: ResultAppealsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        appealsAdapter = ResultAppealsAdapter { appeal ->
            findNavController().navigate(
                R.id.action_result_detail_to_appeal_detail,
                bundleOf(
                    "appealId" to appeal.id,
                    "studentName" to appeal.studentName,
                    "studentCode" to appeal.studentCode
                )
            )
        }
        binding.rvAppeals.adapter = appealsAdapter
        binding.btnCreateAppeal.setOnClickListener { showCreateAppealDialog() }
        binding.cardAppealNotice.setOnClickListener {
            val bundle = bundleOf("examId" to currentResult?.exam?.id.orEmpty())
            findNavController().navigate(R.id.action_result_detail_to_appeals_list, bundle)
        }
        collectOnStarted {
            launch { viewModel.result.collect { it?.let(::bindResult) } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_LONG).show() } }
            launch {
                viewModel.appealCount.collect { count ->
                    bindAppealNotice(count)
                    updateAppealButton()
                }
            }
            launch {
                viewModel.appeals.collect { appeals ->
                    appealsAdapter.submitList(appeals)
                    bindAppealsSection(appeals.size, viewModel.appealsLoaded.value)
                }
            }
            launch {
                viewModel.appealsLoaded.collect { loaded ->
                    bindAppealsSection(viewModel.appeals.value.size, loaded)
                }
            }
            launch { viewModel.isResultPending.collect(::bindPendingState) }
            launch {
                viewModel.resultUnavailable.collect {
                    bindUnavailableState()
                }
            }
            launch {
                viewModel.appealCreated.collect { appealId ->
                    findNavController().navigate(
                        R.id.action_result_detail_to_appeal_detail,
                        bundleOf(
                            "appealId" to appealId,
                            "studentName" to currentResult?.displayStudentName().orEmpty(),
                            "studentCode" to currentResult?.displayStudentCode().orEmpty()
                        )
                    )
                }
            }
        }
        viewModel.loadResult(sheetId, arguments?.getString("examStatus").orEmpty())
    }

    private fun bindResult(result: StudentResultDetailResponse) {
        currentResult = result
        if (result.resultStatus.equals("PENDING", ignoreCase = true) && result.id.isNullOrBlank()) {
            bindPendingResult(result)
            return
        }
        binding.tvExamName.text = result.exam?.name ?: getString(R.string.result_detail_default_title)
        binding.tvSubject.text = result.exam?.subject.orEmpty()
        bindStudentInfo(result)
        binding.tvScore.text = result.totalScore?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
        binding.tvQuestionCount.text = result.exam?.totalQuestions
            ?.let { getString(R.string.result_detail_question_count_format, it) }
            ?: ""
        binding.tvDuration.text = result.exam?.duration
            ?.let { getString(R.string.result_detail_duration_format, it) }
            ?: ""
        binding.tvGradedAt.text = formatGradedAt(result.gradedAt)
        updateAppealButton()
    }

    private fun bindPendingResult(result: StudentResultDetailResponse) {
        binding.tvExamName.text = result.exam?.name ?: getString(R.string.result_detail_default_title)
        binding.tvSubject.text = result.exam?.subject ?: getString(R.string.result_detail_pending_message)
        bindStudentInfo(result)
        binding.tvScore.text = "--"
        binding.tvQuestionCount.text = ""
        binding.tvDuration.text = ""
        binding.tvGradedAt.text = getString(R.string.result_detail_pending_message)
        binding.cardAppealNotice.visibility = View.GONE
        binding.btnCreateAppeal.visibility = View.GONE
        hideAppealsSection()
    }

    private fun bindPendingState(isPending: Boolean) {
        if (isPending) {
            binding.btnCreateAppeal.visibility = View.GONE
            binding.cardAppealNotice.visibility = View.GONE
            hideAppealsSection()
        }
    }

    private fun bindUnavailableState() {
        binding.tvExamName.text = getString(R.string.result_detail_default_title)
        binding.tvSubject.text = getString(R.string.result_detail_pending_message)
        binding.tvScore.text = "--"
        binding.tvQuestionCount.text = ""
        binding.tvDuration.text = ""
        binding.tvGradedAt.text = getString(R.string.result_detail_pending_message)
        binding.tvStudentName.visibility = View.GONE
        binding.cardAppealNotice.visibility = View.GONE
        binding.btnCreateAppeal.visibility = View.GONE
        hideAppealsSection()
    }

    private fun bindStudentInfo(result: StudentResultDetailResponse) {
        val name = result.displayStudentName()
        val code = result.displayStudentCode()
        val text = listOfNotNull(
            name,
            code?.let { getString(R.string.result_detail_student_code_format, it) }
        ).joinToString(" - ")
        binding.tvStudentName.text = text
        binding.tvStudentName.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun canCreateAppeal(result: StudentResultDetailResponse): Boolean {
        return !result.id.isNullOrBlank() &&
            result.resultStatus.equals("GRADED", ignoreCase = true) &&
            result.exam?.status.isAppealOpenStatus() &&
            viewModel.appealCount.value == 0
    }

    private fun updateAppealButton() {
        val result = currentResult
        binding.btnCreateAppeal.visibility = if (result != null && canCreateAppeal(result)) View.VISIBLE else View.GONE
    }

    private fun bindAppealNotice(count: Int) {
        binding.cardAppealNotice.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.tvAppealNotice.text = if (count > 1) {
            getString(R.string.result_detail_has_appeals_notice, count)
        } else {
            getString(R.string.result_detail_has_appeal_notice)
        }
    }

    private fun bindAppealsSection(count: Int, loaded: Boolean) {
        if (currentResult == null || viewModel.isResultPending.value) {
            hideAppealsSection()
            return
        }
        binding.tvAppealsTitle.visibility = View.VISIBLE
        binding.tvAppealsTitle.text = if (count > 0) {
            getString(R.string.result_detail_appeals_count, count)
        } else {
            getString(R.string.result_detail_appeals_title)
        }
        binding.rvAppeals.visibility = if (loaded && count > 0) View.VISIBLE else View.GONE
        binding.tvAppealsEmpty.visibility = if (loaded && count == 0) View.VISIBLE else View.GONE
    }

    private fun hideAppealsSection() {
        binding.tvAppealsTitle.visibility = View.GONE
        binding.rvAppeals.visibility = View.GONE
        binding.tvAppealsEmpty.visibility = View.GONE
    }

    private fun formatGradedAt(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.result_detail_ungraded)
        val formatted = value.toLocalDisplayDateTime(value)
        return getString(R.string.result_detail_graded_at_format, formatted)
    }

    private fun showCreateAppealDialog() {
        val result = currentResult
        if (result == null || !canCreateAppeal(result)) {
            val messageRes = when {
                result?.id.isNullOrBlank() -> R.string.result_detail_missing_sheet
                !result?.exam?.status.isAppealOpenStatus() -> R.string.result_detail_appeal_closed_exam
                viewModel.appealCount.value > 0 -> R.string.result_detail_appeal_pending_exists
                else -> R.string.result_detail_missing_sheet
            }
            Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
            return
        }
        val context = requireContext()
        val dialogBinding = DialogCreateAppealBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.result_detail_appeal_dialog_title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.result_detail_appeal_send, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = dialogBinding.etReason.text?.toString().orEmpty()
                dialogBinding.layoutReason.error =
                    if (reason.isBlank()) getString(R.string.result_detail_reason_required) else null
                if (reason.isBlank()) return@setOnClickListener
                viewModel.createAppeal(reason)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun String?.isAppealOpenStatus(): Boolean {
        return equals("END", ignoreCase = true)
    }
}
