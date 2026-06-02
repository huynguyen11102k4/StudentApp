package com.examhub.student.ui.examlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.PopupMenu
import androidx.core.widget.doAfterTextChanged
import com.examhub.student.R
import com.examhub.student.databinding.FragmentExamListFullBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.showShimmer
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExamListFragment : Fragment() {

    private var _binding: FragmentExamListFullBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamListViewModel by viewModel()
    private lateinit var adapter: ExamPagingAdapter
    private var selectedFilter = ExamListViewModel.ExamFilter.ALL
    private var currentGradingType = ""
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExamListFullBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentGradingType = arguments?.getString("gradingType").orEmpty()
        selectedFilter = arguments?.getString("examFilter").toExamFilter()
        binding.appBar.applySystemWindowInsets(top = true)
        updateToolbarTitle(arguments?.getString("title"))
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setupSearch()
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.actionExamTypeFilter) {
                showFilterMenu()
                true
            } else {
                false
            }
        }

        adapter = ExamPagingAdapter { exam ->
            if (exam.hasSubmitted && !exam.resultSheetId.isNullOrBlank()) {
                val bundle = Bundle().apply { putString("sheetId", exam.resultSheetId) }
                findNavController().navigate(R.id.resultDetailFragment, bundle)
            } else {
                val bundle = Bundle().apply { putString("examId", exam.id) }
                findNavController().navigate(R.id.action_exam_list_to_exam_detail, bundle)
            }
        }
        binding.rvExams.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExams.adapter = adapter

        collectOnStarted {
            launch {
                viewModel.exams.collect { adapter.submitData(it) }
            }
            launch {
                adapter.loadStateFlow.collect { state ->
                    val loading = state.refresh is LoadState.Loading
                    val showSkeleton = loading && adapter.itemCount == 0
                    binding.loadingSkeleton.root.showShimmer(showSkeleton)
                    binding.progressBar.visibility = if (loading && !showSkeleton) View.VISIBLE else View.GONE
                    binding.emptyState.visibility = if (!loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
                    binding.rvExams.visibility = if (showSkeleton || (!loading && adapter.itemCount == 0)) View.GONE else View.VISIBLE
                }
            }
        }

        viewModel.configure(currentGradingType, selectedFilter)
        updateStatusFilterMenuState()
    }

    private fun setupSearch() {
        val searchItem = binding.toolbar.menu.findItem(R.id.actionSearchExam)
        searchItem.isVisible = false
        binding.etSearch.doAfterTextChanged { text ->
            searchQuery = text?.toString().orEmpty()
            viewModel.setSearch(searchQuery)
        }
    }

    private fun showFilterMenu() {
        val anchor = binding.toolbar.findViewById<View>(R.id.actionExamTypeFilter) ?: binding.toolbar
        PopupMenu(requireContext(), anchor).apply {
            menu.add(GROUP_STATUS, MENU_STATUS_ALL, 0, R.string.exam_list_filter_all)
            menu.add(GROUP_STATUS, MENU_STATUS_READY, 1, R.string.exam_list_filter_ready)
            menu.add(GROUP_STATUS, MENU_STATUS_PROCESSING, 2, R.string.exam_list_filter_processing)
            menu.add(GROUP_STATUS, MENU_STATUS_CLOSED, 3, R.string.exam_list_filter_closed)
            menu.setGroupCheckable(GROUP_STATUS, true, true)
            menu.findItem(menuIdForStatus(selectedFilter))?.isChecked = true
            setOnMenuItemClickListener { item ->
                selectedFilter = when (item.itemId) {
                    MENU_STATUS_READY -> ExamListViewModel.ExamFilter.READY
                    MENU_STATUS_PROCESSING -> ExamListViewModel.ExamFilter.PROCESSING
                    MENU_STATUS_CLOSED -> ExamListViewModel.ExamFilter.CLOSED
                    else -> ExamListViewModel.ExamFilter.ALL
                }
                viewModel.setFilter(selectedFilter)
                updateStatusFilterMenuState()
                true
            }
            show()
        }
    }

    private fun updateToolbarTitle(explicitTitle: String?) {
        binding.toolbar.title = explicitTitle.orEmpty().ifBlank {
            when (currentGradingType) {
                "STUDENT_SUBMISSION" -> getString(R.string.dashboard_student_submission_exam_list_title)
                else -> getString(R.string.exam_list_default_title)
            }
        }
    }

    private fun menuIdForStatus(filter: ExamListViewModel.ExamFilter): Int {
        return when (filter) {
            ExamListViewModel.ExamFilter.READY -> MENU_STATUS_READY
            ExamListViewModel.ExamFilter.PROCESSING -> MENU_STATUS_PROCESSING
            ExamListViewModel.ExamFilter.CLOSED -> MENU_STATUS_CLOSED
            ExamListViewModel.ExamFilter.ALL -> MENU_STATUS_ALL
        }
    }

    private fun updateStatusFilterMenuState() {
        val item = binding.toolbar.menu.findItem(R.id.actionExamTypeFilter) ?: return
        item.title = when (selectedFilter) {
            ExamListViewModel.ExamFilter.READY -> getString(R.string.exam_list_filter_ready)
            ExamListViewModel.ExamFilter.PROCESSING -> getString(R.string.exam_list_filter_processing)
            ExamListViewModel.ExamFilter.CLOSED -> getString(R.string.exam_list_filter_closed)
            ExamListViewModel.ExamFilter.ALL -> getString(R.string.class_list_filter)
        }
        item.contentDescription = item.title
    }

    private fun String?.toExamFilter(): ExamListViewModel.ExamFilter {
        return when (orEmpty().trim().uppercase()) {
            "READY", "ACTIVE", "OPEN", "IN_PROGRESS", "RUNNING" -> ExamListViewModel.ExamFilter.READY
            "PROCESSING", "SUBMITTED" -> ExamListViewModel.ExamFilter.PROCESSING
            "CLOSED", "END", "ENDED" -> ExamListViewModel.ExamFilter.CLOSED
            else -> ExamListViewModel.ExamFilter.ALL
        }
    }

    private companion object {
        const val GROUP_STATUS = 1
        const val MENU_STATUS_ALL = 101
        const val MENU_STATUS_READY = 102
        const val MENU_STATUS_PROCESSING = 103
        const val MENU_STATUS_CLOSED = 104
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
