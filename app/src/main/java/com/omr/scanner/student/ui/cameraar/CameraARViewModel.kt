package com.omr.scanner.student.ui.cameraar

import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.LockHeartbeatRequest
import com.omr.scanner.student.model.request.LockViolationRequest
import com.omr.scanner.student.omr.OmrProcessor
import com.omr.scanner.student.omr.OmrReviewStore
import com.omr.scanner.student.repository.LockModeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CameraARViewModel(
    private val omrProcessor: OmrProcessor,
    private val omrReviewStore: OmrReviewStore,
    private val lockModeRepository: LockModeRepository
) : ViewModel() {

    private val _flashMode = MutableStateFlow("off")
    val flashMode: StateFlow<String> = _flashMode.asStateFlow()

    private val _flashAvailable = MutableStateFlow(true)
    val flashAvailable: StateFlow<Boolean> = _flashAvailable.asStateFlow()

    private val _detectedMarkers = MutableStateFlow(0)
    val detectedMarkers: StateFlow<Int> = _detectedMarkers.asStateFlow()

    private val _totalMarkers = MutableStateFlow(12)
    val totalMarkers: StateFlow<Int> = _totalMarkers.asStateFlow()

    private val _markerStatusText = MutableStateFlow("Bấm chụp để xử lý OMR")
    val markerStatusText: StateFlow<String> = _markerStatusText.asStateFlow()

    private val _allMarkersDetected = MutableStateFlow(false)
    val allMarkersDetected: StateFlow<Boolean> = _allMarkersDetected.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _capturedImage = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
    val capturedImage: SharedFlow<Bitmap> = _capturedImage.asSharedFlow()

    private val _navigateToReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToReview: SharedFlow<Unit> = _navigateToReview.asSharedFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _cameraReady = MutableStateFlow(false)
    val cameraReady: StateFlow<Boolean> = _cameraReady.asStateFlow()

    private var currentExamId: String = ""
    private var currentSessionId: String = ""
    private var heartbeatJob: Job? = null

    fun setExamId(examId: String) {
        currentExamId = examId
    }

    fun setSessionId(sessionId: String) {
        currentSessionId = sessionId
        startHeartbeatIfNeeded()
    }

    fun onFlashModeUpdated(mode: String) {
        _flashMode.value = mode
    }

    fun onFlashAvailabilityChanged(available: Boolean) {
        _flashAvailable.value = available
    }

    fun onCameraReady() {
        _cameraReady.value = true
        _markerStatusText.value = "Bấm chụp để xử lý OMR"
    }

    fun onMarkersDetected(detectedCount: Int, totalExpected: Int = 12) {
        _detectedMarkers.value = detectedCount
        _totalMarkers.value = totalExpected

        val allFound = detectedCount >= totalExpected
        _allMarkersDetected.value = allFound

        _markerStatusText.value = when {
            detectedCount == 0 -> "Chưa thấy marker - đưa camera gần hơn"
            allFound -> "Đã nhận diện đủ $detectedCount/$totalExpected marker - bấm chụp để xử lý"
            detectedCount >= totalExpected * 0.75 -> "Gần đủ marker: $detectedCount/$totalExpected"
            else -> "Đã nhận diện $detectedCount/$totalExpected marker"
        }
    }

    fun onImageCaptured(bitmap: Bitmap) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        _markerStatusText.value = "Đang xử lý OMR..."

        if (currentExamId.isBlank()) {
            onProcessingError("Hãy mở camera từ một kỳ thi đã có mẫu OMR")
            return
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    omrProcessor.process(bitmap, currentExamId)
                }
            }.onSuccess { result ->
                val rawImageBase64 = encodeJpegBase64(bitmap)
                omrReviewStore.save(
                    result.copy(
                        sessionId = currentSessionId,
                        rawImageBase64 = rawImageBase64
                    )
                )
                _isProcessing.value = false
                _navigateToReview.tryEmit(Unit)
            }.onFailure { error ->
                onProcessingError(error.message ?: "Xử lý OMR thất bại")
            }
        }
    }

    fun resetProcessingState() {
        _isProcessing.value = false
        _markerStatusText.value = "Bấm chụp để xử lý OMR"
    }

    fun onProcessingComplete() {
        _isProcessing.value = false
    }

    fun onProcessingError(error: String) {
        _isProcessing.value = false
        _toastMessage.tryEmit(error)
        _markerStatusText.value = "Lỗi: $error - thử lại"
    }

    fun logViolation(type: String, evidence: Map<String, Any?> = emptyMap()) {
        val sessionId = currentSessionId.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            lockModeRepository.logViolation(
                LockViolationRequest(
                    sessionId = sessionId,
                    violationType = type,
                    occurredAt = nowIso(),
                    evidenceData = evidence
                )
            ).collect { result ->
                if (result is ApiResult.Error) {
                    _toastMessage.tryEmit(result.exception.message ?: "Không thể ghi nhận vi phạm lock mode")
                }
            }
        }
    }

    fun toggleFlash() {
        // Actual flash toggling is done in CameraManager via CameraARFragment.
    }

    fun updateMarkerCount(count: Int, total: Int) {
        _detectedMarkers.value = count
        _totalMarkers.value = total
    }

    private fun startHeartbeatIfNeeded() {
        if (currentSessionId.isBlank() || heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (true) {
                lockModeRepository.heartbeat(
                    currentSessionId,
                    LockHeartbeatRequest(
                        network = mapOf("type" to "unknown", "strength" to "unknown"),
                        appInForeground = true
                    )
                ).collect { result ->
                    if (result is ApiResult.Error) {
                        _toastMessage.tryEmit(result.exception.message ?: "Không thể gửi heartbeat")
                    }
                }
                delay(30_000)
            }
        }
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private fun encodeJpegBase64(bitmap: Bitmap): String {
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }
}
