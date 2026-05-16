package com.omr.scanner.student.ui.appeals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.omr.scanner.student.BuildConfig
import com.omr.scanner.student.R
import com.omr.scanner.student.data.model.Appeal
import com.omr.scanner.student.databinding.FragmentAppealDetailBinding
import com.omr.scanner.student.ui.applySystemWindowInsets
import com.omr.scanner.student.ui.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppealDetailFragment : Fragment() {

    private var _binding: FragmentAppealDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppealDetailViewModel by viewModel()
    private var currentAppeal: Appeal? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppealDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnBackToAppeals.visibility = View.GONE

        collectOnStarted {
            launch {
                viewModel.appeal.collect { appeal ->
                    appeal?.let(::bindAppeal)
                }
            }
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
        currentAppeal = appeal
        binding.tvStudentName.text = appeal.studentName
        binding.tvStudentCode.text = "Mã HS: ${appeal.studentCode}"
        binding.tvExamName.text = appeal.examName
        binding.tvSubject.text = "Môn: ${appeal.subject}"
        binding.tvOldScore.text = "${appeal.newScore ?: appeal.oldScore}đ"
        binding.tvReason.text = appeal.reason
        binding.tvCreatedAt.text = "Gửi lúc: ${appeal.createdAt.toDisplayDateTime()}"
        binding.tvDetectedInfo.text = buildDetectedInfo(appeal)
        binding.chipStatus.text = when (appeal.status) {
            "PENDING" -> "Chờ xử lý"
            "ACCEPTED", "RESOLVED" -> "Đã chấp nhận"
            "REJECTED" -> "Từ chối"
            else -> appeal.status
        }
        val imageUrl = appeal.processedImageUrl ?: appeal.dewarpedImageUrl
        if (imageUrl.isNullOrBlank()) {
            binding.cardProcessedImage.visibility = View.GONE
        } else {
            binding.cardProcessedImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(resolveUrl(imageUrl))
                .centerCrop()
                .into(binding.ivProcessedImage)
        }
    }

    private fun buildDetectedInfo(appeal: Appeal): String {
        return listOf(
            "Mã học sinh: ${appeal.studentCode.ifBlank { "Chưa xác định" }}",
            "Điểm hiện tại: ${appeal.oldScore}đ",
            appeal.newScore?.let { "Điểm sau xử lý: ${it}đ" },
            appeal.itemMessages.takeIf { it.isNotBlank() }
        ).filterNotNull().joinToString("\n")
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
