package com.examhub.student.ui.cameraar

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.helper.protectScreenFromCapture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CameraARFragment : Fragment() {

    private var _binding: FragmentCameraArBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraARViewModel by viewModel()
    private var cameraManager: CameraManager? = null
    private var hasCameraPermission = false
    private var sessionTimeoutJob: Job? = null
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
        viewModel.resetProcessingState()
        if (_binding != null) {
            showCaptureLoading(false)
        }
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
                viewModel.onImageCaptured(bitmap)
            },
            onCaptureFailed = { error ->
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    showCaptureLoading(false)
                    binding.fabCapture.isEnabled = !viewModel.isProcessing.value
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.camera_ar_capture_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onMarkersDetected = { detected, expected ->
                viewModel.onMarkersDetected(detected, expected)
            },
            onAutoCaptureReady = {
                autoCapturePhoto()
            }
        )
        cameraManager?.startCamera()
        viewModel.onCameraReady()
        viewModel.onFlashAvailabilityChanged(cameraManager?.isFlashAvailable() ?: false)
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener {
            cameraManager?.shutdown()
            findNavController().navigateUp()
        }

        binding.btnFlash.setOnClickListener {
            val newMode = cameraManager?.toggleFlash() ?: "off"
            viewModel.onFlashModeUpdated(newMode)
            updateFlashIcon(newMode)
        }

        binding.fabCapture.setOnClickListener {
            capturePhoto(showNotReadyMessage = true)
        }

        binding.btnGallery.setOnClickListener {
            if (viewModel.isProcessing.value) return@setOnClickListener
            pickImageLauncher.launch("image/*")
        }
    }

    private fun autoCapturePhoto() {
        if (_binding == null || viewModel.isProcessing.value) return
        viewModel.onAutoCaptureStarting()
        capturePhoto(showNotReadyMessage = false)
    }

    private fun capturePhoto(showNotReadyMessage: Boolean) {
        if (viewModel.isProcessing.value) return
        val started = cameraManager?.capturePhoto() == true
        if (!started) {
            showCaptureLoading(false)
            if (showNotReadyMessage) {
                Toast.makeText(requireContext(), R.string.camera_ar_camera_not_ready, Toast.LENGTH_SHORT).show()
            }
        } else {
            showCaptureLoading(true)
        }
    }

    private fun handleGalleryImage(uri: Uri) {
        val bitmap = runCatching { decodeGalleryBitmap(uri) }.getOrElse { error ->
            Toast.makeText(
                requireContext(),
                error.message ?: getString(R.string.camera_ar_gallery_read_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        showCaptureLoading(true)
        viewModel.onImageCaptured(bitmap)
    }

    private fun decodeGalleryBitmap(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return decodeGalleryBitmapWithImageDecoder(uri)
        }

        val resolver = requireContext().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: error(getString(R.string.camera_ar_gallery_open_failed))

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error(getString(R.string.camera_ar_invalid_image))
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
        } ?: error(getString(R.string.camera_ar_gallery_decode_failed))
    }

    private fun decodeGalleryBitmapWithImageDecoder(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(requireContext().contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 4096
            val width = info.size.width
            val height = info.size.height
            if (width <= 0 || height <= 0) {
                error(getString(R.string.camera_ar_invalid_image))
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
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
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
        binding.btnFlash.isEnabled = !show
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
                viewModel.markerStatusText.collect { status ->
                    binding.tvMarkerStatus.text = status
                }
            }
            launch {
                viewModel.isProcessing.collect { processing ->
                    showCaptureLoading(processing)
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
                        putLong("timerStartedAt", System.currentTimeMillis())
                    }
                    findNavController().navigate(R.id.action_camera_ar_to_smart_review, bundle)
                }
            }
            launch {
                viewModel.toastMessage.collect { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
        cameraManager?.shutdown()
        (requireActivity() as? MainActivity)?.exitKioskMode()
        Toast.makeText(requireContext(), R.string.lock_mode_time_expired, Toast.LENGTH_LONG).show()
        findNavController().navigate(
            R.id.submissionEndFragment,
            Bundle().apply {
                putString("examId", arguments?.getString("examId").orEmpty())
                putString("sheetId", "")
            },
            navOptions {
                popUpTo(R.id.lockModeFragment) { inclusive = true }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionTimeoutJob?.cancel()
        viewModel.stopSessionWork()
        cameraManager?.shutdown()
        cameraManager = null
        _binding = null
    }
}
