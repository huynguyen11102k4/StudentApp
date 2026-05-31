package com.examhub.student.ui.smartreview

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.MainActivity
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSmartReviewBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.helper.protectScreenFromCapture
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class SmartReviewFragment : Fragment() {

    private var _binding: FragmentSmartReviewBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SmartReviewViewModel by viewModel()
    private var sessionTimeoutJob: Job? = null
    private var finishingExpiredSession = false
    private val writeStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                saveResultImageToGallery()
            } else {
                Snackbar.make(binding.root, R.string.smart_review_storage_permission_required, Snackbar.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSmartReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        protectScreenFromCapture()
        binding.topBar.applySystemWindowInsets(top = true)
        binding.topBar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnRetake.setOnClickListener { openCameraForCurrentExam() }
        binding.btnContinueCapture.setOnClickListener {
            showSubmitConfirmDialog()
        }
        binding.btnDownloadImage.setOnClickListener { downloadResultImage() }

        collectOnStarted {
            launch {
                viewModel.reviewState.collect { state ->
                    bindReviewState(state)
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.savedSuccess.collect { submission ->
                    Snackbar.make(binding.root, R.string.smart_review_submitted, Snackbar.LENGTH_SHORT).show()
                    val bundle = Bundle().apply {
                        putString("examId", viewModel.reviewState.value.examId)
                        putString("submissionId", submission.submissionId)
                        putString("sheetId", submission.resultId.orEmpty())
                    }
                    findNavController().navigate(R.id.action_smart_review_to_submission_end, bundle)
                }
            }
            launch {
                viewModel.errorMessage.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    if (finishingExpiredSession) finishExpiredSession()
                }
            }
        }

        viewModel.loadReviewData(arguments?.getString("submissionId").orEmpty())
        viewModel.setFallbackSession(arguments?.getString("sessionId").orEmpty())
        startSessionTimeout()
    }

    private fun openCameraForCurrentExam() {
        val examId = viewModel.reviewState.value.examId.ifBlank {
            arguments?.getString("examId").orEmpty()
        }
        if (examId.isBlank()) {
            Snackbar.make(binding.root, R.string.smart_review_missing_exam, Snackbar.LENGTH_LONG).show()
            return
        }

        val navController = findNavController()
        val returnedToCamera = navController.popBackStack(R.id.cameraARFragment, false)
        if (!returnedToCamera) {
            val bundle = Bundle().apply {
                putString("examId", examId)
                putString("sessionId", viewModel.reviewState.value.sessionId)
                putInt("remainingSeconds", remainingSeconds())
                putLong("timerStartedAt", System.currentTimeMillis())
            }
            navController.navigate(R.id.action_smart_review_to_camera_ar, bundle)
        }
    }

    private fun showSubmitConfirmDialog() {
        val state = viewModel.reviewState.value
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.smart_review_submit_confirm_title)
            .setMessage(R.string.smart_review_submit_confirm_message)
            .setNegativeButton(R.string.smart_review_submit_confirm_review, null)
            .setPositiveButton(R.string.smart_review_submit_confirm_submit) { _, _ ->
                viewModel.saveResults(state.score ?: 0.0, state.examId, state.studentId)
            }
            .show()
    }

    private fun startSessionTimeout() {
        val seconds = remainingSeconds()
        if (seconds <= 0) return
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(seconds * 1_000L)
            handleSessionExpired()
        }
    }

    private fun handleSessionExpired() {
        if (_binding == null || finishingExpiredSession) return
        finishingExpiredSession = true
        val state = viewModel.reviewState.value
        if (state.hasOmrResult && !viewModel.isLoading.value) {
            Snackbar.make(binding.root, R.string.lock_mode_time_expired_auto_submit, Snackbar.LENGTH_LONG).show()
            viewModel.saveResults(state.score ?: 0.0, state.examId, state.studentId)
        } else {
            finishExpiredSession()
        }
    }

    private fun finishExpiredSession() {
        if (_binding == null) return
        (requireActivity() as? MainActivity)?.exitKioskMode()
        findNavController().navigate(
            R.id.submissionEndFragment,
            Bundle().apply {
                putString("examId", viewModel.reviewState.value.examId.ifBlank { arguments?.getString("examId").orEmpty() })
                putString("sheetId", "")
            },
            navOptions {
                popUpTo(R.id.lockModeFragment) { inclusive = true }
            }
        )
    }

    private fun remainingSeconds(): Int {
        val initial = arguments?.getInt("remainingSeconds") ?: 0
        val startedAt = arguments?.getLong("timerStartedAt") ?: 0L
        if (startedAt <= 0L) return initial
        val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000L).toInt()
        return (initial - elapsed).coerceAtLeast(0)
    }

    private fun bindReviewState(state: ReviewUiState) {
        val emptyValue = getString(R.string.smart_review_null_value)
        binding.tvScore.text = getString(R.string.smart_review_detection_title)
        binding.tvStudentName.text = getString(R.string.smart_review_student_name_format, state.studentName ?: emptyValue)
        binding.tvStudentCode.text = getString(R.string.smart_review_student_code_format, state.studentId.ifBlank { emptyValue })
        binding.tvClassCode.text = getString(R.string.smart_review_class_code_format, state.classCode ?: emptyValue)
        binding.tvExamCode.text = getString(R.string.smart_review_exam_code_format, state.examCode ?: emptyValue)
        bindDebugImage(state.debugImageBase64)
    }

    private fun downloadResultImage() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            writeStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        saveResultImageToGallery()
    }

    private fun saveResultImageToGallery() {
        val base64 = viewModel.reviewState.value.debugImageBase64
        if (base64.isBlank()) {
            Snackbar.make(binding.root, R.string.smart_review_missing_image, Snackbar.LENGTH_SHORT).show()
            return
        }

        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val fileName = "omr-result-${System.currentTimeMillis()}.jpg"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScopedGalleryImage(bytes, fileName)
            } else {
                saveLegacyGalleryImage(bytes, fileName)
            }
        }.onSuccess {
            Snackbar.make(binding.root, R.string.smart_review_download_success, Snackbar.LENGTH_SHORT).show()
        }.onFailure {
            Snackbar.make(binding.root, it.message ?: getString(R.string.smart_review_missing_image), Snackbar.LENGTH_LONG).show()
        }
    }

    private fun saveScopedGalleryImage(bytes: ByteArray, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OMR")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error(getString(R.string.smart_review_create_image_file_failed))
        resolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: error(getString(R.string.smart_review_write_image_file_failed))

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun saveLegacyGalleryImage(bytes: ByteArray, fileName: String) {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val omrDir = File(picturesDir, "OMR")
        if (!omrDir.exists() && !omrDir.mkdirs()) {
            error(getString(R.string.smart_review_create_gallery_dir_failed))
        }

        val file = File(omrDir, fileName)
        file.outputStream().use { output ->
            output.write(bytes)
        }
        MediaScannerConnection.scanFile(
            requireContext(),
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null
        )
    }

    private fun bindDebugImage(base64: String) {
        if (base64.isBlank()) {
            binding.ivDebugImage.setImageDrawable(null)
            binding.ivDebugImage.visibility = View.GONE
            binding.tvNoDebugImage.visibility = View.VISIBLE
            return
        }

        runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onSuccess { bitmap ->
            if (bitmap == null) {
                binding.ivDebugImage.setImageDrawable(null)
                binding.ivDebugImage.visibility = View.GONE
                binding.tvNoDebugImage.visibility = View.VISIBLE
            } else {
                binding.ivDebugImage.setImageBitmap(bitmap)
                binding.ivDebugImage.visibility = View.VISIBLE
                binding.tvNoDebugImage.visibility = View.GONE
            }
        }.onFailure {
            binding.ivDebugImage.setImageDrawable(null)
            binding.ivDebugImage.visibility = View.GONE
            binding.tvNoDebugImage.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionTimeoutJob?.cancel()
        _binding = null
    }
}
