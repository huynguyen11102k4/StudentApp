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
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.ui.applySystemWindowInsets
import com.examhub.student.ui.collectOnStarted
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvAnswers.adapter = adapter
        binding.btnCreateAppeal.setOnClickListener { showCreateAppealDialog() }
        collectOnStarted {
            launch { viewModel.result.collect { it?.let(::bindResult) } }
            launch { viewModel.isLoading.collect { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE } }
            launch { viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() } }
            launch {
                viewModel.appealCreated.collect { appealId ->
                    findNavController().navigate(R.id.action_result_detail_to_appeal_detail, bundleOf("appealId" to appealId))
                }
            }
        }
        viewModel.loadResult(sheetId)
    }

    private fun bindResult(result: StudentResultDetailResponse) {
        binding.tvExamName.text = result.exam?.name ?: getString(R.string.result_detail_default_title)
        binding.tvSubject.text = result.exam?.subject.orEmpty()
        binding.tvScore.text = result.totalScore?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
        binding.tvQuestionCount.text = result.exam?.totalQuestions
            ?.let { getString(R.string.result_detail_question_count_format, it) }
            ?: ""
        binding.tvDuration.text = result.exam?.duration
            ?.let { getString(R.string.result_detail_duration_format, it) }
            ?: ""
        binding.tvGradedAt.text = formatGradedAt(result.gradedAt)
        adapter.submitList(result.answerDetails)
        val imageUrl = result.processedImageUrl ?: result.dewarpedImageUrl ?: result.rawImageUrl
        if (imageUrl.isNullOrBlank()) {
            binding.imageCard.visibility = View.GONE
            binding.ivResult.visibility = View.GONE
        } else {
            binding.imageCard.visibility = View.VISIBLE
            binding.ivResult.visibility = View.VISIBLE
            Glide.with(this).load(imageUrl).into(binding.ivResult)
        }
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
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val reasonInput = TextInputEditText(context)
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
        container.addView(reasonLayout)
        container.addView(questionLayout)
        container.addView(messageLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.result_detail_appeal_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.result_detail_appeal_send) { _, _ ->
                viewModel.createAppeal(
                    reason = reasonInput.text?.toString().orEmpty(),
                    questionNumber = questionInput.text?.toString()?.toIntOrNull(),
                    questionMessage = messageInput.text?.toString().orEmpty()
                )
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
