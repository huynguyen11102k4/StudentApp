package com.examhub.student.ui.classlist

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.R
import com.examhub.student.databinding.FragmentClassListBinding
import com.examhub.student.ui.applySystemWindowInsets
import com.examhub.student.ui.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassListFragment : Fragment() {

    private var _binding: FragmentClassListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassListViewModel by viewModel()
    private lateinit var adapter: ClassListAdapter
    private var allClasses = emptyList<com.examhub.student.data.model.SchoolClass>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ClassListAdapter(
            onItemClick = { schoolClass ->
                findNavController().navigate(R.id.action_class_list_to_class_detail, bundleOf("classId" to schoolClass.id))
            }
        )
        binding.rvClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClasses.adapter = adapter
        binding.fabJoinClass.setOnClickListener { showJoinClassDialog() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                submitFilteredClasses(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        collectOnStarted {
            launch {
                viewModel.classes.collect { classes ->
                    allClasses = classes
                    submitFilteredClasses(binding.etSearch.text?.toString().orEmpty())
                }
            }
            launch {
                viewModel.message.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.loadClasses()
    }

    private fun submitFilteredClasses(query: String) {
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isBlank()) {
            allClasses
        } else {
            allClasses.filter { schoolClass ->
                schoolClass.name.contains(normalizedQuery, ignoreCase = true) ||
                    schoolClass.subject.contains(normalizedQuery, ignoreCase = true) ||
                    schoolClass.joinCode.contains(normalizedQuery, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        binding.tvClassCount.text = getString(R.string.class_list_count, filtered.size)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvClasses.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showJoinClassDialog() {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.class_list_join_code_sample)
            setPadding(32, 8, 32, 0)
        }
        val joinCodeInput = TextInputEditText(inputLayout.context)
        inputLayout.addView(joinCodeInput)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.student_join_class_title)
            .setView(inputLayout)
            .setPositiveButton(R.string.student_join_class_action) { _, _ ->
                viewModel.joinClass(joinCodeInput.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
