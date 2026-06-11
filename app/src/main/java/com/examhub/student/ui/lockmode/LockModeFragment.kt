package com.examhub.student.ui.lockmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentLockModeBinding
import com.examhub.student.kiosk.KioskModeState
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.helper.protectScreenFromCapture
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class LockModeFragment : Fragment() {
    private var _binding: FragmentLockModeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LockModeViewModel by viewModel()
    private var monitor: LockModeMonitor? = null

    private val examId: String get() = arguments?.getString("examId").orEmpty()
    private val sessionId: String get() = arguments?.getString("sessionId").orEmpty()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLockModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        protectScreenFromCapture()
        enterKioskMode()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            }
        )
        binding.btnOpenCamera.setOnClickListener {
            val resolvedSessionId = viewModel.currentSessionId.value.ifBlank { sessionId }
            val bundle = Bundle().apply {
                putString("examId", examId)
                putString("sessionId", resolvedSessionId)
                putInt("remainingSeconds", viewModel.remainingSeconds.value)
                putInt("questionCount", viewModel.currentQuestionCount.value)
                putLong("timerStartedAt", System.currentTimeMillis())
            }
            findNavController().navigate(R.id.action_lock_mode_to_camera_ar, bundle)
        }
        setupMonitor()
        viewModel.start(
            sessionId = sessionId,
            examId = examId,
            initialSeconds = arguments?.getInt("remainingSeconds") ?: 0,
            questionCount = arguments?.getInt("questionCount") ?: 0,
            argCodes = LockModeOmrCodes(
                classCode = arguments?.getString("classCode").orEmpty(),
                studentCode = arguments?.getString("studentCode").orEmpty(),
                studentCodeMode = arguments?.getString("studentCodeMode").orEmpty()
            )
        )

        collectOnStarted {
            launch {
                viewModel.remainingSeconds.collect { seconds ->
                    binding.tvTimer.text = formatTimer(seconds)
                }
            }
            launch {
                viewModel.omrCodes.collect { codes ->
                    binding.tvStudentCode.text = codes.studentCode.ifBlank { getString(R.string.lock_mode_code_empty) }
                    binding.tvClassCode.text = codes.classCode.ifBlank { getString(R.string.lock_mode_code_empty) }
                }
            }
            launch {
                viewModel.timeExpired.collect {
                    finishExpiredSession()
                }
            }
            launch {
                viewModel.blankSubmissionFinished.collect { submission ->
                    navigateToSubmissionEnd(submission?.resultId)
                }
            }
        }
        viewModel.flushViolations()
    }

    private fun enterKioskMode() {
        val state = (requireActivity() as? MainActivity)?.enterKioskMode()
        val message = when (state) {
            KioskModeState.DeviceOwnerLocked -> getString(R.string.lock_mode_kiosk_device_owner)
            KioskModeState.ScreenPinned -> getString(R.string.lock_mode_kiosk_screen_pinned)
            is KioskModeState.Unavailable -> getString(R.string.lock_mode_kiosk_unavailable, state.reason)
            null -> getString(R.string.lock_mode_kiosk_activity_unavailable)
        }
        binding.tvKioskStatus.text = message
    }

    private fun setupMonitor() {
        monitor = LockModeMonitor(
            context = requireContext().applicationContext,
            onNetworkLost = {
                viewModel.markNetworkOffline(
                    mapOf(
                        "screen" to "lock_mode",
                        "violation_label" to getString(R.string.lock_violation_network_lost_label),
                        "teacher_message" to getString(R.string.lock_violation_network_lost_teacher_message)
                    )
                )
            },
            onNetworkAvailable = {
                viewModel.markNetworkRestored(
                    mapOf(
                        "screen" to "lock_mode",
                        "violation_label" to getString(R.string.lock_violation_network_restored_label),
                        "teacher_message" to getString(R.string.lock_violation_network_restored_teacher_message)
                    )
                )
            },
            onScreenOff = {
                viewModel.logViolation(
                    "screen_off",
                    mapOf(
                        "screen" to "lock_mode",
                        "violation_label" to getString(R.string.lock_violation_screen_off_label),
                        "teacher_message" to getString(R.string.lock_violation_screen_off_teacher_message)
                    )
                )
            }
        ).also { it.start() }
    }

    private fun finishExpiredSession() {
        binding.btnOpenCamera.isEnabled = false
        viewModel.submitBlankOnTimeout()
        viewModel.stopSessionWork()
        (requireActivity() as? MainActivity)?.exitKioskMode()
        Snackbar.make(binding.root, R.string.lock_mode_time_expired, Snackbar.LENGTH_SHORT).show()
    }

    private fun navigateToSubmissionEnd(resultId: String?) {
        if (_binding == null) return
        findNavController().navigate(
            R.id.submissionEndFragment,
            Bundle().apply {
                putString("examId", examId)
                putString("sheetId", resultId.orEmpty())
            },
            navOptions {
                popUpTo(R.id.lockModeFragment) { inclusive = true }
            }
        )
    }

    private fun formatTimer(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monitor?.stop()
        monitor = null
        viewModel.stopSessionWork()
        _binding = null
    }
}
