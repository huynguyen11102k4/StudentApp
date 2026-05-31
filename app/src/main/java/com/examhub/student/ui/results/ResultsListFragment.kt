package com.examhub.student.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentResultsListBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ResultsListFragment : Fragment() {
    private var _binding: FragmentResultsListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultsListViewModel by viewModel()
    private lateinit var adapter: ResultsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResultsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        adapter = ResultsAdapter { result ->
            findNavController().navigate(R.id.action_results_list_to_result_detail, bundleOf("sheetId" to result.id))
        }
        binding.rvResults.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadResults() }
        collectOnStarted {
            launch {
                viewModel.results.collect {
                    adapter.submitList(it)
                    binding.emptyState.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.isLoading.collect {
                    binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE
                    binding.swipeRefresh.isRefreshing = it && adapter.itemCount > 0
                }
            }
            launch {
                viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
            }
        }
        viewModel.loadResults()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
