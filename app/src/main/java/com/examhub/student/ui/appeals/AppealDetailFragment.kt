package com.examhub.student.ui.appeals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.examhub.student.BuildConfig
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.databinding.FragmentAppealDetailBinding
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import com.examhub.student.extension.toFriendlyAppealStatus
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
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        viewModel.loadAppeal(arguments?.getString("appealId").orEmpty())
    }

    private fun bindAppeal(appeal: Appeal) {
        binding.tvStudentName.text = appeal.studentName.ifBlank { "Học sinh" }
        binding.tvStudentCode.text = "Mã HS: ${appeal.studentCode.ifBlank { "Chưa xác định" }}"
        binding.tvExamName.text = appeal.examName.ifBlank { getString(R.string.result_detail_default_title) }
        binding.tvSubject.text = appeal.subject.takeIf { it.isNotBlank() }?.let { "Môn: $it" }.orEmpty()
        binding.tvOldScore.text = appeal.newScore?.let {
            getString(R.string.appeal_score_changed_format, appeal.oldScore, it)
        } ?: getString(R.string.appeal_score_format, appeal.oldScore)
        binding.tvReason.text = appeal.reason.ifBlank { "Không có ghi chú" }
        binding.tvCreatedAt.text = "Gửi lúc: ${appeal.createdAt.toDisplayDateTime()}"
        binding.tvDetectedInfo.text = buildDetectedInfo(appeal)
        binding.chipStatus.text = appeal.status.toFriendlyAppealStatus()
        binding.cardAppealItems.visibility = if (binding.tvDetectedInfo.text.isNullOrBlank()) View.GONE else View.VISIBLE

        val imageUrl = appeal.processedImageUrl ?: appeal.dewarpedImageUrl
        if (imageUrl.isNullOrBlank()) {
            binding.cardProcessedImage.visibility = View.GONE
        } else {
            binding.cardProcessedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(resolveUrl(imageUrl))
                .fitCenter()
                .into(binding.ivProcessedImage)
        }
    }

    private fun buildDetectedInfo(appeal: Appeal): String {
        return listOfNotNull(
            appeal.itemMessages.takeIf { it.isNotBlank() },
            appeal.teacherNote?.takeIf { it.isNotBlank() }?.let { "Phản hồi giáo viên: $it" }
        ).joinToString("\n\n")
    }

    private fun resolveUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val apiBase = BuildConfig.API_BASE_URL.trimEnd('/')
        val origin = apiBase.substringBefore("/api/v1")
        return origin + if (url.startsWith("/")) url else "/$url"
    }

    private fun String.toDisplayDateTime(): String {
        return replace("T", " ")
            .removeSuffix("Z")
            .substringBefore(".")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
