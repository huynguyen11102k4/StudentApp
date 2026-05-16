package com.omr.scanner.student.ui.appeals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.omr.scanner.student.R
import com.omr.scanner.student.databinding.FragmentAppealsListBinding
import com.omr.scanner.student.ui.applySystemWindowInsets
import com.omr.scanner.student.ui.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AppealsListFragment : Fragment() {

    private var _binding: FragmentAppealsListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppealsListViewModel by viewModel()
    private lateinit var adapter: AppealAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppealsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = AppealAdapter { appeal ->
            val bundle = Bundle().apply { putString("appealId", appeal.id) }
            findNavController().navigate(R.id.action_appeals_list_to_appeal_detail, bundle)
        }
        binding.rvAppeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAppeals.adapter = adapter

        collectOnStarted {
            launch {
                viewModel.appeals.collect { appeals ->
                    adapter.submitList(appeals)
                    binding.toolbar.subtitle = "${appeals.size} khiếu nại"
                    binding.emptyState.visibility = if (appeals.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
        viewModel.loadAppeals(arguments?.getString("examId").orEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
