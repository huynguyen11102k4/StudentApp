package com.examhub.student.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.R
import com.examhub.student.databinding.FragmentResultDetailBinding
import com.examhub.student.model.response.result.StudentResultAnswerResponse
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ResultDetailFragment : Fragment() {
    private var _binding: FragmentResultDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultDetailViewModel by viewModel()
    private val adapter = ResultAnswerAdapter()
    private val sheetId: String get() = arguments?.getString("sheetId").orEmpty()
    private var currentResult: StudentResultDetailResponse? = null
    private var answersExpanded = false
    private var answerDetails = emptyList<StudentResultAnswerResponse>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvAnswers.adapter = adapter
        binding.answersHeader.setOnClickListener { setAnswersExpanded(!answersExpanded) }
        binding.btnToggleAnswers.setOnClickListener { setAnswersExpanded(!answersExpanded) }
        binding.btnCreateAppeal.setOnClickListener { showCreateAppealDialog() }
        binding.cardAppealNotice.setOnClickListener {
            val bundle = bundleOf("examId" to currentResult?.exam?.id.orEmpty())
            findNavController().navigate(R.id.action_result_detail_to_appeals_list, bundle)
        }
        collectOnStarted {
            launch { viewModel.result.collect { it?.let(::bindResult) } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
            launch { viewModel.appealCount.collect(::bindAppealNotice) }
            launch { viewModel.isResultPending.collect(::bindPendingState) }
            launch {
                viewModel.appealCreated.collect { appealId ->
                    findNavController().navigate(R.id.action_result_detail_to_appeal_detail, bundleOf("appealId" to appealId))
                }
            }
        }
        viewModel.loadResult(sheetId)
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
        binding.tvScore.text = result.totalScore?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.tvQuestionCount.text = result.exam?.totalQuestions
            ?.let { getString(R.string.result_detail_question_count_format, it) }
            ?: ""
        binding.tvDuration.text = result.exam?.duration
            ?.let { getString(R.string.result_detail_duration_format, it) }
            ?: ""
        binding.tvGradedAt.text = formatGradedAt(result.gradedAt)
        answerDetails = result.answerDetails
        setAnswersExpanded(false)
        val imageUrl = result.processedImageUrl ?: result.dewarpedImageUrl ?: result.rawImageUrl
        if (imageUrl.isNullOrBlank()) {
            binding.imageCard.visibility = View.GONE
            binding.ivResult.visibility = View.GONE
        } else {
            binding.imageCard.visibility = View.VISIBLE
            binding.ivResult.visibility = View.VISIBLE
            Glide.with(this).load(imageUrl).into(binding.ivResult)
        }
        binding.answersHeader.visibility = View.VISIBLE
        binding.btnToggleAnswers.visibility = View.VISIBLE
        binding.btnCreateAppeal.visibility = if (canCreateAppeal(result)) View.VISIBLE else View.GONE
    }

    private fun bindPendingResult(result: StudentResultDetailResponse) {
        binding.tvExamName.text = result.exam?.name ?: getString(R.string.result_detail_default_title)
        binding.tvSubject.text = result.exam?.subject ?: getString(R.string.result_detail_pending_message)
        bindStudentInfo(result)
        binding.tvScore.text = "--"
        binding.tvQuestionCount.text = ""
        binding.tvDuration.text = ""
        binding.tvGradedAt.text = getString(R.string.result_detail_pending_message)
        binding.imageCard.visibility = View.GONE
        binding.ivResult.visibility = View.GONE
        binding.answersHeader.visibility = View.GONE
        binding.btnToggleAnswers.visibility = View.GONE
        binding.rvAnswers.visibility = View.GONE
        binding.cardAppealNotice.visibility = View.GONE
        binding.btnCreateAppeal.visibility = View.GONE
        answerDetails = emptyList()
        adapter.submitList(emptyList())
    }

    private fun bindPendingState(isPending: Boolean) {
        if (isPending) {
            binding.btnCreateAppeal.visibility = View.GONE
            binding.cardAppealNotice.visibility = View.GONE
        }
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
        return !result.id.isNullOrBlank() && !result.exam?.status.equals("CLOSED", ignoreCase = true)
    }

    private fun bindAppealNotice(count: Int) {
        binding.cardAppealNotice.visibility = if (count > 0) View.VISIBLE else View.GONE
        binding.tvAppealNotice.text = if (count > 1) {
            getString(R.string.result_detail_has_appeals_notice, count)
        } else {
            getString(R.string.result_detail_has_appeal_notice)
        }
    }

    private fun setAnswersExpanded(expanded: Boolean) {
        answersExpanded = expanded
        binding.btnToggleAnswers.setIconResource(
            if (expanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
        )
        binding.rvAnswers.visibility = if (expanded) View.VISIBLE else View.GONE
        if (expanded) {
            binding.rvAnswers.layoutParams = binding.rvAnswers.layoutParams.apply {
                height = answerListHeightPx(answerDetails.size)
            }
            adapter.submitList(answerDetails)
        } else {
            adapter.submitList(emptyList())
            binding.rvAnswers.layoutParams = binding.rvAnswers.layoutParams.apply {
                height = 0
            }
        }
    }

    private fun answerListHeightPx(itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val density = resources.displayMetrics.density
        val estimatedItemHeight = (96 * density).toInt()
        val maxHeight = (420 * density).toInt()
        return (itemCount * estimatedItemHeight).coerceAtMost(maxHeight)
    }

    private fun formatGradedAt(value: String?): String {
        if (value.isNullOrBlank()) return getString(R.string.result_detail_ungraded)
        val output = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        val formatted = parsers.firstNotNullOfOrNull { parser ->
            runCatching { parser.parse(value)?.let(output::format) }.getOrNull()
        } ?: value
        return getString(R.string.result_detail_graded_at_format, formatted)
    }

    private fun showCreateAppealDialog() {
        val result = currentResult
        if (result == null || !canCreateAppeal(result)) {
            val messageRes = if (result?.exam?.status.equals("CLOSED", ignoreCase = true)) {
                R.string.result_detail_appeal_closed_exam
            } else {
                R.string.result_detail_missing_sheet
            }
            Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
            return
        }
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val intro = android.widget.TextView(context).apply {
            text = getString(R.string.result_detail_appeal_dialog_message)
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        val reasonInput = TextInputEditText(context).apply {
            minLines = 2
            maxLines = 4
        }
        val reasonLayout = TextInputLayout(context).apply {
            hint = getString(R.string.result_detail_appeal_reason_hint)
            addView(reasonInput)
        }
        val questionInput = TextInputEditText(context).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val questionLayout = TextInputLayout(context).apply {
            hint = getString(R.string.result_detail_appeal_question_hint)
            addView(questionInput)
        }
        val messageInput = TextInputEditText(context)
        val messageLayout = TextInputLayout(context).apply {
            hint = getString(R.string.result_detail_appeal_message_hint)
            addView(messageInput)
        }
        container.addView(intro)
        container.addView(reasonLayout)
        container.addView(questionLayout)
        container.addView(messageLayout)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.result_detail_appeal_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.result_detail_appeal_send, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = reasonInput.text?.toString().orEmpty()
                reasonLayout.error = if (reason.isBlank()) getString(R.string.result_detail_reason_required) else null
                if (reason.isBlank()) return@setOnClickListener
                viewModel.createAppeal(
                    reason = reason,
                    questionNumber = questionInput.text?.toString()?.toIntOrNull(),
                    questionMessage = messageInput.text?.toString().orEmpty()
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
