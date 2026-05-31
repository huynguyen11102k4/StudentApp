package com.examhub.student.ui.examlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import com.examhub.student.R
import com.examhub.student.databinding.FragmentExamListFullBinding
import com.examhub.student.ui.dashboard.RecentExamAdapter
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ExamListFragment : Fragment() {

    private var _binding: FragmentExamListFullBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamListViewModel by viewModel()
    private lateinit var adapter: RecentExamAdapter
    private var allExams = emptyList<com.examhub.student.data.model.Exam>()
    private var selectedFilter = ExamFilter.ALL
    private var currentGradingType = ""
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExamListFullBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentGradingType = arguments?.getString("gradingType").orEmpty()
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

        adapter = RecentExamAdapter { exam ->
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
                viewModel.exams.collect { exams ->
                    allExams = exams
                    submitFilteredExams()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }

        viewModel.load(currentGradingType)
    }

    private fun setupSearch() {
        val searchItem = binding.toolbar.menu.findItem(R.id.actionSearchExam)
        val searchView = searchItem.actionView as? SearchView ?: return
        searchView.queryHint = getString(R.string.exam_list_search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText.orEmpty()
                submitFilteredExams()
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : android.view.MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: android.view.MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: android.view.MenuItem): Boolean {
                searchQuery = ""
                submitFilteredExams()
                return true
            }
        })
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
                    MENU_STATUS_READY -> ExamFilter.READY
                    MENU_STATUS_PROCESSING -> ExamFilter.PROCESSING
                    MENU_STATUS_CLOSED -> ExamFilter.CLOSED
                    else -> ExamFilter.ALL
                }
                submitFilteredExams()
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

    private fun menuIdForStatus(filter: ExamFilter): Int {
        return when (filter) {
            ExamFilter.READY -> MENU_STATUS_READY
            ExamFilter.PROCESSING -> MENU_STATUS_PROCESSING
            ExamFilter.CLOSED -> MENU_STATUS_CLOSED
            ExamFilter.ALL -> MENU_STATUS_ALL
        }
    }

    private fun submitFilteredExams() {
        val statusFiltered = when (selectedFilter) {
            ExamFilter.ALL -> allExams
            ExamFilter.READY -> allExams.filter { it.isOpenForStudent() && !it.hasSubmitted }
            ExamFilter.PROCESSING -> allExams.filter { it.hasSubmitted || it.isSubmittedLikeStatus() }
            ExamFilter.CLOSED -> allExams.filter { it.isClosedStatus() }
        }
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) {
            statusFiltered
        } else {
            statusFiltered.filter { exam ->
                exam.name.contains(query, ignoreCase = true) ||
                    exam.subject.contains(query, ignoreCase = true) ||
                    exam.className.contains(query, ignoreCase = true) ||
                    exam.status.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvExams.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private enum class ExamFilter {
        ALL,
        READY,
        PROCESSING,
        CLOSED
    }

    private fun com.examhub.student.data.model.Exam.isOpenForStudent(): Boolean {
        val normalized = status.uppercase()
        return normalized.isBlank() ||
            listOf("OPEN", "ACTIVE", "PUBLISHED", "STARTED", "READY").any { normalized.contains(it) }
    }

    private fun com.examhub.student.data.model.Exam.isSubmittedLikeStatus(): Boolean {
        val normalized = status.uppercase()
        return listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any { normalized.contains(it) }
    }

    private fun com.examhub.student.data.model.Exam.isClosedStatus(): Boolean {
        val normalized = status.uppercase()
        return listOf("CLOSED", "END", "ENDED", "EXPIRED", "LOCKED").any { normalized == it || normalized.contains(it) }
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
