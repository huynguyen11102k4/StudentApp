package com.examhub.student.ui.lockmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentLockModeBinding
import com.examhub.student.kiosk.KioskModeState
import com.examhub.student.ui.collectOnStarted
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
        enterKioskMode()
        binding.btnOpenCamera.setOnClickListener {
            val bundle = Bundle().apply {
                putString("examId", examId)
                putString("sessionId", sessionId)
            }
            findNavController().navigate(R.id.action_lock_mode_to_camera_ar, bundle)
        }
        binding.btnSubmit.setOnClickListener { showSubmitHintDialog() }
        setupMonitor()

        collectOnStarted {
            launch {
                viewModel.remainingSeconds.collect { seconds ->
                    binding.tvTimer.text = formatTimer(seconds)
                }
            }
            launch {
                viewModel.message.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
            }
            launch {
                viewModel.queuedViolationCount.collect { count ->
                    binding.tvViolationQueue.text = if (count > 0) {
                        getString(R.string.lock_mode_queue_format, count)
                    } else {
                        ""
                    }
                }
            }
            launch {
                viewModel.timeExpired.collect {
                    binding.btnOpenCamera.isEnabled = false
                    Snackbar.make(binding.root, R.string.lock_mode_time_expired, Snackbar.LENGTH_INDEFINITE).show()
                }
            }
        }
        viewModel.start(sessionId, arguments?.getInt("remainingSeconds") ?: 0)
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
                viewModel.logViolation("untrusted", mapOf("reason" to "network_lost", "screen" to "lock_mode"))
            },
            onNetworkAvailable = {
                viewModel.flushViolations()
            },
            onScreenOff = {
                viewModel.logViolation("screen_off", mapOf("screen" to "lock_mode"))
            }
        ).also { it.start() }
    }

    private fun showSubmitHintDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.lock_mode_submit_hint_title)
            .setMessage(R.string.lock_mode_submit_hint_message)
            .setNegativeButton(R.string.lock_mode_submit_hint_close, null)
            .setPositiveButton(R.string.lock_mode_submit_hint_open_camera) { _, _ -> binding.btnOpenCamera.performClick() }
            .show()
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
        _binding = null
    }
}
