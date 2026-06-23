package com.examhub.student.ui.classlist

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.R
import com.examhub.student.databinding.FragmentClassListBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.showShimmer
import com.examhub.student.util.extension.replaceTechnicalLabels
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ClassListFragment : Fragment() {

    private var _binding: FragmentClassListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClassListViewModel by viewModel()
    private lateinit var adapter: ClassListAdapter

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
                viewModel.setSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        collectOnStarted {
            launch {
                viewModel.classes.collect { adapter.submitData(it) }
            }
            launch {
                adapter.loadStateFlow.collect { state ->
                    val loading = state.refresh is LoadState.Loading
                    val showSkeleton = loading && adapter.itemCount == 0
                    binding.tvClassCount.text = getString(R.string.class_list_count, adapter.itemCount)
                    binding.loadingSkeleton.root.showShimmer(showSkeleton)
                    binding.emptyState.visibility = if (!loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
                    binding.rvClasses.visibility = if (showSkeleton || (!loading && adapter.itemCount == 0)) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.message.collect { message ->
                    if (message.isNotBlank()) {
                        Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            launch {
                viewModel.joinResult.collect { success ->
                    if (success) {
                        Snackbar.make(binding.root, R.string.class_list_join_success, Snackbar.LENGTH_SHORT).show()
                        adapter.refresh()
                    } else {
                        Snackbar.make(binding.root, R.string.class_list_join_code_empty, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

    }

    private fun showJoinClassDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 0)
        }
        val joinCodeLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.class_list_join_code_sample)
        }
        val joinCodeInput = TextInputEditText(joinCodeLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        joinCodeLayout.addView(joinCodeInput)
        val studentCodeLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.class_list_student_code_hint)
        }
        val studentCodeInput = TextInputEditText(studentCodeLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(viewModel.defaultStudentCode.value)
            setSelection(text?.length ?: 0)
        }
        studentCodeLayout.addView(studentCodeInput)
        container.addView(joinCodeLayout)
        container.addView(studentCodeLayout)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.student_join_class_title)
            .setView(container)
            .setPositiveButton(R.string.student_join_class_action, null)
            .setNegativeButton(R.string.common_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val joinCode = joinCodeInput.text?.toString().orEmpty()
                val studentCode = studentCodeInput.text?.toString().orEmpty()
                joinCodeLayout.error = if (joinCode.isBlank()) getString(R.string.class_list_join_code_empty) else null
                studentCodeLayout.error = if (studentCode.isBlank()) getString(R.string.class_list_student_code_empty) else null
                if (joinCode.isBlank() || studentCode.isBlank()) return@setOnClickListener
                viewModel.joinClass(
                    joinCode = joinCode,
                    studentCode = studentCode
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
