package com.omr.scanner.student.ui.submissionend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.omr.scanner.student.MainActivity
import com.omr.scanner.student.R
import com.omr.scanner.student.databinding.FragmentSubmissionEndBinding

class SubmissionEndFragment : Fragment() {
    private var _binding: FragmentSubmissionEndBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubmissionEndBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as? MainActivity)?.exitKioskMode()
        binding.btnResults.setOnClickListener {
            findNavController().navigate(R.id.action_submission_end_to_results)
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
