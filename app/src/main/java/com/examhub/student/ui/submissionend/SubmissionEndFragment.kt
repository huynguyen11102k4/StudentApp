package com.examhub.student.ui.submissionend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSubmissionEndBinding
import com.examhub.student.util.extension.collectOnStarted
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class SubmissionEndFragment : Fragment() {
    private var _binding: FragmentSubmissionEndBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmissionEndViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionEndBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as? MainActivity)?.exitKioskMode()
        viewModel.observeSubmission(arguments?.getString("clientSubmissionId").orEmpty())
        binding.btnResults.setOnClickListener {
            viewModel.openResult(
                sheetId = arguments?.getString("sheetId").orEmpty(),
                examId = arguments?.getString("examId").orEmpty()
            )
        }
        collectOnStarted {
            launch {
                viewModel.isResolving.collect { resolving ->
                    binding.btnResults.isEnabled = !resolving
                }
            }
            launch {
                viewModel.navigation.collect { destination ->
                    when (destination) {
                        is SubmissionEndViewModel.ResultNavigation.Detail -> {
                            findNavController().navigate(
                                R.id.action_submission_end_to_result_detail,
                                bundleOf(
                                    "sheetId" to destination.sheetId,
                                    "examStatus" to destination.examStatus
                                )
                            )
                        }
                    }
                }
            }
            launch {
                viewModel.message.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
            launch {
                viewModel.syncStatus.collect { statusRes ->
                    binding.tvSyncStatus.visibility = if (statusRes == null) View.GONE else View.VISIBLE
                    if (statusRes != null) binding.tvSyncStatus.setText(statusRes)
                }
            }
        }
        binding.btnHome.setOnClickListener {
            findNavController().navigate(R.id.dashboardFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
