package com.examhub.student.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.examhub.student.R
import com.examhub.student.databinding.FragmentDashboardBinding
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.service.BackendUrlManager
import com.examhub.student.util.extension.add3DTouch
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.showShimmer
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.helper.UploadUrlResolver
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModel()
    private val backendUrlManager: BackendUrlManager by inject()
    private lateinit var recentExamsAdapter: RecentExamAdapter
    private var recentExamCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        binding.appBar.applySystemWindowInsets(top = true)
        setupClickListeners()
        setup3DTouch()
        observeViewModel()
        viewModel.loadDashboard()
    }

    private fun setup3DTouch() {
        binding.cardOnline.add3DTouch()
        binding.cardClasses.add3DTouch()
        binding.btnResults.add3DTouch(scaleTo = 0.98f)
        binding.btnNotifications.add3DTouch(scaleTo = 0.92f)
        binding.btnSettings.add3DTouch(scaleTo = 0.92f)
    }

    private fun setupRecyclerView() {
        recentExamsAdapter = RecentExamAdapter { exam ->
            if (exam.canViewResult && !exam.resultSheetId.isNullOrBlank()) {
                openSubmittedExam(exam)
            } else if (exam.hasSubmitted || exam.resultOnly) {
                findNavController().navigate(R.id.action_dashboard_to_results)
            } else {
                val bundle = Bundle().apply { putString("examId", exam.id) }
                findNavController().navigate(R.id.action_dashboard_to_exam_detail, bundle)
            }
        }
        binding.rvRecentExams.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentExams.adapter = recentExamsAdapter
    }

    private fun setupClickListeners() {
        binding.cardOnline.setOnClickListener {
            val bundle = Bundle().apply {
                putString("gradingType", "")
                putString("examFilter", "READY")
                putString("title", getString(R.string.dashboard_camera_exam_list_title))
            }
            findNavController().navigate(R.id.action_dashboard_to_exam_list, bundle)
        }
        binding.cardClasses.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_class_list)
        }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_settings)
        }
        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_notifications)
        }
        binding.btnViewAllExams.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_exam_list, allExamsBundle())
        }
        binding.btnResults.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_results)
        }
    }

    private fun allExamsBundle(): Bundle {
        return Bundle().apply {
            putString("gradingType", "")
            putString("title", getString(R.string.exam_list_default_title))
        }
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.profile.collect { profile ->
                    profile?.let(::bindProfile)
                }
            }
            launch {
                viewModel.recentExams.collect { exams ->
                    recentExamCount = exams.size
                    recentExamsAdapter.submitList(exams)
                    updateRecentExamLoadingState(viewModel.isLoading.value)
                }
            }
            launch {
                viewModel.classCount.collect { count ->
                    binding.tvClassSummary.text = getString(R.string.dashboard_classes_subtitle, count)
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    updateRecentExamLoadingState(loading)
                }
            }
            launch {
                viewModel.toastMessage.collect {
                    com.google.android.material.snackbar.Snackbar.make(binding.root, it.replaceTechnicalLabels(), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSubmittedExam(exam: com.examhub.student.data.model.Exam) {
        val resultId = exam.resultSheetId
        if (resultId.isNullOrBlank()) {
            findNavController().navigate(R.id.action_dashboard_to_results)
        } else {
            findNavController().navigate(
                R.id.resultDetailFragment,
                Bundle().apply {
                    putString("sheetId", resultId)
                    putString("examStatus", exam.status)
                }
            )
        }
    }

    private fun updateRecentExamLoadingState(loading: Boolean) {
        val showSkeleton = loading && recentExamCount == 0
        binding.loadingSkeletonRecentExams.root.showShimmer(showSkeleton)
        binding.progressBar.visibility = if (loading && !showSkeleton) View.VISIBLE else View.GONE
        binding.emptyRecentExams.visibility = if (!loading && recentExamCount == 0) View.VISIBLE else View.GONE
        binding.rvRecentExams.visibility = if (showSkeleton || recentExamCount == 0) View.GONE else View.VISIBLE
    }

    private fun bindProfile(profile: UserResponse) {
        val fullName: String? = profile.fullName
        val displayName = fullName.orEmpty()
        binding.tvStudentName.text = displayName
            .takeIf { it.isNotBlank() }
            ?.let { getString(R.string.dashboard_title_student_name, it) }
            ?: getString(R.string.dashboard_title_student)
        binding.tvStudentInitials.text = initials(displayName)

        val avatarUrl = UploadUrlResolver.resolveUploadUrl(profile.avatarUrl, backendUrlManager.currentBaseUrl())
        if (avatarUrl == null) {
            binding.ivStudentAvatar.visibility = View.GONE
            binding.tvStudentInitials.visibility = View.VISIBLE
            binding.ivStudentAvatar.setImageDrawable(null)
            return
        }

        binding.ivStudentAvatar.visibility = View.VISIBLE
        binding.tvStudentInitials.visibility = View.GONE
        Glide.with(this)
            .load(avatarUrl)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .circleCrop()
            .error(R.drawable.bg_avatar)
            .into(binding.ivStudentAvatar)
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .takeLast(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "HS" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
