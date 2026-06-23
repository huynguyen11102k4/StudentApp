package com.examhub.student.ui.cameraar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentCameraArBinding
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.helper.protectScreenFromCapture
import com.examhub.student.ui.lockmode.LockFlowMonitorController
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class CameraARFragment : Fragment() {

    private var _binding: FragmentCameraArBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraARViewModel by viewModel()
    private val lockModeRepository: LockModeRepository by inject()
    private var cameraManager: CameraManager? = null
    private var hasCameraPermission = false
    private var sessionTimeoutJob: Job? = null
    private var lockFlowMonitor: LockFlowMonitorController? = null
    private var captureInFlight = false
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleGalleryImage(it) }
        }

    // Permission launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), R.string.camera_ar_permission_required, Toast.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraArBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        protectScreenFromCapture()
        enterKioskModeIfLocked()
        setupLockFlowMonitor()
        viewModel.resetProcessingState()
        viewModel.setExamId(arguments?.getString("examId").orEmpty())
        viewModel.setSessionId(arguments?.getString("sessionId").orEmpty())
        binding.topInfo.visibility = View.VISIBLE
        checkCameraPermission()
        setupClickListeners()
        observeViewModel()
        startSessionTimeout()
    }

    override fun onResume() {
        super.onResume()
        enterKioskModeIfLocked()
    }

    private fun enterKioskModeIfLocked() {
        if (arguments?.getString("sessionId").isNullOrBlank()) return
        (requireActivity() as? MainActivity)?.enterKioskMode()
    }

    private fun setupLockFlowMonitor() {
        val sessionId = arguments?.getString("sessionId").orEmpty()
        if (sessionId.isBlank()) return
        lockFlowMonitor = LockFlowMonitorController(
            context = requireContext(),
            lockModeRepository = lockModeRepository,
            scope = viewLifecycleOwner.lifecycleScope,
            sessionIdProvider = { arguments?.getString("sessionId").orEmpty() },
            screenName = "camera_ar"
        ).also { it.start() }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                hasCameraPermission = true
                startCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                Snackbar.make(
                    binding.root,
                    getString(R.string.camera_ar_permission_sheet),
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.camera_ar_permission_allow) {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                }.show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        if (!hasCameraPermission) return

        cameraManager = CameraManager(
            lifecycleOwner = viewLifecycleOwner,
            previewView = binding.previewView,
            onImageCaptured = { bitmap ->
                processCapturedBitmap(bitmap)
            },
            onCaptureFailed = { error ->
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    handleCaptureFailure(error)
                }
            },
            onMarkersDetected = { detected, expected ->
                viewModel.onMarkersDetected(detected, expected)
            },
            onAutoCaptureReady = {
                if (_binding != null && !viewModel.isProcessing.value && !captureInFlight) {
                    captureInFlight = true
                    viewModel.onAutoCaptureStarting()
                    showCaptureLoading(true)
                    true
                } else {
                    false
                }
            },
            onCameraBound = { flashAvailable ->
                viewModel.onCameraReady()
                viewModel.onFlashAvailabilityChanged(flashAvailable)
            }
        )
        cameraManager?.startCamera()
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            val flashMode = cameraManager?.turnFlashOff() ?: "off"
            viewModel.onFlashModeUpdated(flashMode)
            cameraManager?.shutdown()
            findNavController().navigateUp()
        }

        binding.btnFlash.setOnClickListener {
            val newMode = cameraManager?.toggleFlash() ?: "off"
            viewModel.onFlashModeUpdated(newMode)
            updateFlashIcon(newMode)
        }

        binding.fabCapture.setOnClickListener {
            requestCameraCapture(showNotReadyMessage = true)
        }

        binding.btnGallery.setOnClickListener {
            if (viewModel.isProcessing.value || captureInFlight) return@setOnClickListener
            pickImageLauncher.launch("image/*")
        }
    }

    private fun requestCameraCapture(showNotReadyMessage: Boolean) {
        if (viewModel.isProcessing.value || captureInFlight) return
        val markersReady = viewModel.allMarkersDetected.value && cameraManager?.hasRecentFullMarkerFrame() == true
        if (!markersReady) {
            showCaptureLoading(false)
            if (showNotReadyMessage) {
                Toast.makeText(requireContext(), R.string.camera_ar_need_all_markers, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val started = cameraManager?.capturePhoto() == true
        if (!started) {
            showCaptureLoading(false)
            if (showNotReadyMessage) {
                Toast.makeText(requireContext(), R.string.camera_ar_camera_not_ready, Toast.LENGTH_SHORT).show()
            }
        } else {
            captureInFlight = true
            showCaptureLoading(true)
        }
    }

    private fun processCapturedBitmap(bitmap: Bitmap) {
        val hostActivity = activity
        if (hostActivity == null) {
            bitmap.recycle()
            captureInFlight = false
            return
        }
        hostActivity.runOnUiThread {
            if (_binding == null || viewModel.isProcessing.value) {
                captureInFlight = false
                bitmap.recycle()
                return@runOnUiThread
            }
            captureInFlight = true
            val flashMode = cameraManager?.turnFlashOff() ?: "off"
            viewModel.onFlashModeUpdated(flashMode)
            updateFlashIcon(flashMode)
            showCaptureLoading(true)
            viewModel.onImageCaptured(bitmap)
        }
    }

    private fun handleCaptureFailure(error: Throwable) {
        captureInFlight = false
        val flashMode = cameraManager?.turnFlashOff() ?: "off"
        viewModel.onFlashModeUpdated(flashMode)
        updateFlashIcon(flashMode)
        showCaptureLoading(false)
        binding.fabCapture.isEnabled = !viewModel.isProcessing.value
        Toast.makeText(
            requireContext(),
            error.message ?: getString(R.string.camera_ar_capture_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun handleGalleryImage(uri: Uri) {
        if (viewModel.isProcessing.value || captureInFlight) return
        captureInFlight = true
        showCaptureLoading(true)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = try {
                withContext(Dispatchers.IO) { decodeGalleryBitmap(appContext, uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                captureInFlight = false
                if (_binding != null) {
                    showCaptureLoading(false)
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.camera_ar_gallery_read_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            if (_binding == null) {
                bitmap.recycle()
                captureInFlight = false
                return@launch
            }
            processCapturedBitmap(bitmap)
        }
    }

    private fun decodeGalleryBitmap(context: Context, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeGalleryBitmapWithImageDecoder(context, uri)
        }

        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error(context.getString(R.string.camera_ar_gallery_open_failed))

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error(context.getString(R.string.camera_ar_invalid_image))
        }

        val maxDimension = 4096
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: error(context.getString(R.string.camera_ar_gallery_decode_failed))
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeGalleryBitmapWithImageDecoder(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 4096
            val width = info.size.width
            val height = info.size.height
            if (width <= 0 || height <= 0) {
                error(context.getString(R.string.camera_ar_invalid_image))
            }

            val scale = minOf(1f, maxDimension.toFloat() / maxOf(width, height).toFloat())
            if (scale < 1f) {
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }.let { bitmap ->
            if (bitmap.config == Bitmap.Config.ARGB_8888) {
                bitmap
            } else {
                bitmap.copy(Bitmap.Config.ARGB_8888, false).also { bitmap.recycle() }
            }
        }
    }

    private fun updateFlashIcon(mode: String) {
        binding.btnFlash.setImageResource(
            when (mode) {
                "on" -> R.drawable.ic_flash_on
                "auto" -> R.drawable.ic_flash_auto
                else -> R.drawable.ic_flash_off
            }
        )
    }

    private fun showCaptureLoading(show: Boolean) {
        binding.progressOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.fabCapture.isEnabled = !show
        binding.btnGallery.isEnabled = !show
        binding.btnFlash.isEnabled = !show && viewModel.flashAvailable.value
        binding.btnClose.isEnabled = !show
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.flashMode.collect { mode ->
                    updateFlashIcon(mode)
                    binding.btnFlash.contentDescription = when (mode) {
                        "on" -> getString(R.string.camera_ar_flash_on)
                        "auto" -> getString(R.string.camera_ar_flash_auto)
                        else -> getString(R.string.camera_ar_flash_off)
                    }
                }
            }
            launch {
                viewModel.flashAvailable.collect { available ->
                    binding.btnFlash.visibility = if (available) View.VISIBLE else View.INVISIBLE
                    binding.btnFlash.isEnabled = available && !viewModel.isProcessing.value
                    if (!available) {
                        viewModel.onFlashModeUpdated("off")
                    }
                }
            }
            launch {
                viewModel.markerStatusText.collect { status ->
                    binding.tvMarkerStatus.text = status
                }
            }
            launch {
                viewModel.isProcessing.collect { processing ->
                    if (!processing) {
                        captureInFlight = false
                    }
                    showCaptureLoading(processing)
                }
            }
            launch {
                viewModel.allMarkersDetected.collect { allDetected ->
                    updateMarkerUi(allDetected)
                }
            }
            launch {
                viewModel.navigateToReview.collect {
                    val bundle = Bundle().apply {
                        putString("submissionId", "")
                        putString("examId", arguments?.getString("examId").orEmpty())
                        putString("sessionId", arguments?.getString("sessionId").orEmpty())
                        putString("studentId", "")
                        putInt("remainingSeconds", remainingSeconds())
                        putInt("questionCount", arguments?.getInt("questionCount") ?: 0)
                        putLong("timerStartedAt", System.currentTimeMillis())
                    }
                    findNavController().navigate(R.id.action_camera_ar_to_smart_review, bundle)
                }
            }
            launch {
                viewModel.toastMessage.collect { msg ->
                    Toast.makeText(requireContext(), msg.replaceTechnicalLabels(), Toast.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.blankSubmissionFinished.collect { submission ->
                    navigateToSubmissionEnd(submission?.resultId, submission?.submissionId)
                }
            }
            launch {
                viewModel.blankSubmissionFrozen.collect { clientSubmissionId ->
                    navigateToSubmissionEnd(null, null, clientSubmissionId)
                }
            }
        }
    }

    private fun startSessionTimeout() {
        val seconds = remainingSeconds()
        if (seconds <= 0) return
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(seconds * 1_000L)
            finishExpiredSession()
        }
    }

    private fun updateMarkerUi(allDetected: Boolean) {
        val overlay = binding.scanOverlay.background?.mutate() as? GradientDrawable ?: return
        val color = ContextCompat.getColor(
            requireContext(),
            if (allDetected) R.color.camera_overlay_ready else R.color.camera_overlay_idle
        )
        overlay.setStroke(
            resources.getDimensionPixelSize(R.dimen.camera_scan_overlay_stroke),
            color,
            resources.getDimension(R.dimen.camera_scan_overlay_dash_width),
            resources.getDimension(R.dimen.camera_scan_overlay_dash_gap)
        )
        binding.scanOverlay.background = overlay

        binding.tvMarkerStatus.setBackgroundResource(
            if (allDetected) R.drawable.bg_badge_ready else R.drawable.bg_badge_processing
        )
        binding.tvMarkerStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (allDetected) R.color.status_ready_text else R.color.status_processing_text
            )
        )
    }

    private fun remainingSeconds(): Int {
        val initial = arguments?.getInt("remainingSeconds") ?: 0
        val startedAt = arguments?.getLong("timerStartedAt") ?: 0L
        if (startedAt <= 0L) return initial
        val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
        return (initial - elapsed).coerceAtLeast(0)
    }

    private fun finishExpiredSession() {
        if (_binding == null) return
        viewModel.stopSessionWork()
        viewModel.submitBlankOnTimeout(arguments?.getInt("questionCount") ?: 0)
        val flashMode = cameraManager?.turnFlashOff() ?: "off"
        viewModel.onFlashModeUpdated(flashMode)
        cameraManager?.shutdown()
        (requireActivity() as? MainActivity)?.exitKioskMode()
        Toast.makeText(requireContext(), R.string.lock_mode_time_expired, Toast.LENGTH_LONG).show()
    }

    private fun navigateToSubmissionEnd(
        resultId: String?,
        submissionId: String? = null,
        clientSubmissionId: String = ""
    ) {
        if (_binding == null) return
        findNavController().navigate(
            R.id.submissionEndFragment,
            Bundle().apply {
                putString("examId", arguments?.getString("examId").orEmpty())
                putString("sheetId", resultId.orEmpty())
                putString("submissionId", submissionId.orEmpty())
                putString("clientSubmissionId", clientSubmissionId)
            },
            navOptions {
                popUpTo(R.id.lockModeFragment) { inclusive = true }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionTimeoutJob?.cancel()
        lockFlowMonitor?.stop()
        lockFlowMonitor = null
        viewModel.stopSessionWork()
        cameraManager?.turnFlashOff()
        cameraManager?.shutdown()
        cameraManager = null
        _binding = null
    }
}
