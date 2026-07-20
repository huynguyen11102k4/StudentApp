package com.examhub.student.ui.manualcrop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentManualCropBinding
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.util.helper.protectScreenFromCapture
import org.koin.android.ext.android.inject

class ManualCropFragment : Fragment() {

    private var _binding: FragmentManualCropBinding? = null
    private val binding get() = _binding!!
    private val activeSessionStore: ActiveExamSessionStore by inject()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManualCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        protectScreenFromCapture()
        enterKioskModeIfLocked()
        binding.btnCancel.setOnClickListener { findNavController().navigateUp() }
        binding.btnConfirm.setOnClickListener {
            findNavController().navigate(
                R.id.action_manual_crop_to_smart_review,
                Bundle().apply {
                    putString("examId", arguments?.getString("examId").orEmpty())
                    putString("sessionId", arguments?.getString("sessionId").orEmpty())
                    putInt("remainingSeconds", remainingSeconds())
                    putInt("questionCount", arguments?.getInt("questionCount") ?: 0)
                    putLong("timerStartedAt", System.currentTimeMillis())
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enterKioskModeIfLocked()
    }

    private fun enterKioskModeIfLocked() {
        if (arguments?.getString("sessionId").isNullOrBlank()) return
        (requireActivity() as? MainActivity)?.enterKioskMode()
    }

    private fun remainingSeconds(): Int {
        val examId = arguments?.getString("examId").orEmpty()
        val sessionId = arguments?.getString("sessionId").orEmpty()
        activeSessionStore.getIncludingExpired(examId)
            ?.takeIf { sessionId.isBlank() || it.sessionId == sessionId }
            ?.currentRemainingSeconds()
            ?.let { return it }
        val initial = arguments?.getInt("remainingSeconds") ?: 0
        val startedAt = arguments?.getLong("timerStartedAt") ?: 0L
        if (startedAt <= 0L) return initial.coerceAtLeast(0)
        val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
        return (initial - elapsed).coerceAtLeast(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
