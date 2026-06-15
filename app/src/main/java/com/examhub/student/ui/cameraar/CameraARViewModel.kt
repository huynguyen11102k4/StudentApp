package com.examhub.student.ui.cameraar

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.request.submission.IdZoneResultRequest
import com.examhub.student.model.request.submission.StudentAnswerRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.model.response.submission.StudentSubmitResponse
import com.examhub.student.omr.OmrProcessor
import com.examhub.student.omr.OmrReviewStore
import com.examhub.student.omr.core.MarkerDetectionException
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.data.local.model.FreezeResult
import com.examhub.student.service.NetworkStatusProvider
import com.examhub.student.service.OfflineSubmissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val lockModeRepository: LockModeRepository,
    private val context: Context,
    private val activeSessionStore: ActiveExamSessionStore,
    private val offlineSubmissionManager: OfflineSubmissionManager
) : ViewModel() {

    private val _flashMode = MutableStateFlow("off")
    val flashMode: StateFlow<String> = _flashMode.asStateFlow()

    private val _flashAvailable = MutableStateFlow(false)
    val flashAvailable: StateFlow<Boolean> = _flashAvailable.asStateFlow()

    private val _detectedMarkers = MutableStateFlow(0)
    val detectedMarkers: StateFlow<Int> = _detectedMarkers.asStateFlow()

    private val _totalMarkers = MutableStateFlow(12)
    val totalMarkers: StateFlow<Int> = _totalMarkers.asStateFlow()

    private val _markerStatusText = MutableStateFlow(context.getString(R.string.camera_ar_status_tap_capture))
    val markerStatusText: StateFlow<String> = _markerStatusText.asStateFlow()

    private val _allMarkersDetected = MutableStateFlow(false)
    val allMarkersDetected: StateFlow<Boolean> = _allMarkersDetected.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _capturedImage = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
    val capturedImage: SharedFlow<Bitmap> = _capturedImage.asSharedFlow()

    private val _navigateToReview = Channel<Unit>(Channel.BUFFERED)
    val navigateToReview = _navigateToReview.receiveAsFlow()
    private val _blankSubmissionFinished = MutableSharedFlow<StudentSubmitResponse?>(extraBufferCapacity = 1)
    val blankSubmissionFinished: SharedFlow<StudentSubmitResponse?> = _blankSubmissionFinished.asSharedFlow()
    private val _blankSubmissionFrozen = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val blankSubmissionFrozen: SharedFlow<String> = _blankSubmissionFrozen.asSharedFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _cameraReady = MutableStateFlow(false)
    val cameraReady: StateFlow<Boolean> = _cameraReady.asStateFlow()

    private var currentExamId: String = ""
    private var currentSessionId: String = ""
    private var heartbeatJob: Job? = null
    private var stopped = false

    fun setExamId(examId: String) {
        currentExamId = examId
    }

    fun setSessionId(sessionId: String) {
        currentSessionId = sessionId
        stopped = false
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
        _markerStatusText.value = context.getString(R.string.camera_ar_status_align_markers)
    }

    fun onMarkersDetected(detectedCount: Int, totalExpected: Int = 12) {
        _detectedMarkers.value = detectedCount
        _totalMarkers.value = totalExpected

        val allFound = detectedCount >= totalExpected
        _allMarkersDetected.value = allFound

        _markerStatusText.value = when {
            detectedCount == 0 -> context.getString(R.string.camera_ar_status_no_marker)
            allFound -> context.getString(R.string.camera_ar_status_all_markers, detectedCount, totalExpected)
            detectedCount >= totalExpected * 0.75 -> context.getString(R.string.camera_ar_status_nearly_markers, detectedCount, totalExpected)
            else -> context.getString(R.string.camera_ar_status_detected_markers, detectedCount, totalExpected)
        }
    }

    fun onAutoCaptureStarting() {
        if (_isProcessing.value) return
        _markerStatusText.value = context.getString(R.string.camera_ar_status_auto_capture)
    }

    fun onImageCaptured(bitmap: Bitmap) {
        if (_isProcessing.value) {
            bitmap.recycle()
            return
        }
        _isProcessing.value = true
        _markerStatusText.value = context.getString(R.string.camera_ar_status_processing_omr)

        if (currentExamId.isBlank()) {
            onProcessingError(context.getString(R.string.camera_ar_missing_exam_template))
            bitmap.recycle()
            return
        }

        viewModelScope.launch {
            try {
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
                    _navigateToReview.trySend(Unit)
                }.onFailure { error ->
                    onProcessingError(localizedProcessingError(error))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun resetProcessingState() {
        if (_isProcessing.value) return
        _isProcessing.value = false
        _markerStatusText.value = context.getString(R.string.camera_ar_status_align_markers)
    }

    fun onProcessingComplete() {
        _isProcessing.value = false
    }

    fun onProcessingError(error: String) {
        _isProcessing.value = false
        _toastMessage.tryEmit(error)
        _markerStatusText.value = context.getString(R.string.camera_ar_status_error_format, error)
    }

    private fun localizedProcessingError(error: Throwable): String = when (error) {
        is MarkerDetectionException.NotEnoughMarkers ->
            context.getString(
                R.string.omr_error_not_enough_markers,
                error.found,
                error.required
            )
        is MarkerDetectionException.MissingRequiredMarkers ->
            context.getString(
                R.string.omr_error_missing_corner_markers,
                error.markerIds.joinToString(", ")
            )
        else -> error.message ?: context.getString(R.string.camera_ar_processing_failed)
    }

    fun logViolation(type: String, evidence: Map<String, Any?> = emptyMap()) {
        if (stopped) return
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
                    // Violation sync is intentionally silent for students.
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
            while (!stopped) {
                lockModeRepository.heartbeat(
                    currentSessionId,
                    LockHeartbeatRequest(
                        network = NetworkStatusProvider.currentNetwork(context),
                        appInForeground = true
                    )
                ).collect { result ->
                    if (result is ApiResult.Error) {
                        if (result.exception.isTerminalSessionStatusError()) {
                            stopSessionWork()
                            return@collect
                        }
                    }
                }
                delay(30_000)
            }
        }
    }

    fun stopSessionWork() {
        stopped = true
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun submitBlankOnTimeout(questionCount: Int) {
        val sessionId = currentSessionId.takeIf { it.isNotBlank() } ?: return
        val examId = currentExamId
        viewModelScope.launch {
            val result = offlineSubmissionManager.freezeAndSync(
                sessionId = sessionId,
                examId = examId,
                requestFactory = { clientSubmissionId, capturedAt ->
                    StudentSubmitRequest(
                    clientSubmissionId = clientSubmissionId,
                    rawImageUrl = null,
                    dewarpedImageUrl = null,
                    processedImageUrl = null,
                    scannedStudentId = null,
                    scannedClassCode = null,
                    scannedExamCode = null,
                    idResult = IdZoneResultRequest(
                        studentId = null,
                        classCode = null,
                        examCode = null,
                        idOk = false,
                        idError = "time_expired_no_scan"
                    ),
                    studentAnswers = (1..questionCount.coerceAtLeast(0)).map { questionNo ->
                        StudentAnswerRequest(questionNumber = questionNo, answer = null)
                    },
                    capturedAt = capturedAt,
                    imageQualityScore = 0,
                    qualityFeedback = mapOf(
                        "auto_submitted" to "true",
                        "reason" to "time_expired_no_scan",
                        "exam_id" to examId
                    )
                )
                }
            )
            activeSessionStore.clear(examId)
            activeSessionStore.clearBySessionId(sessionId)
            when (result) {
                is FreezeResult.Synced -> _blankSubmissionFinished.tryEmit(result.response)
                is FreezeResult.Pending -> _blankSubmissionFrozen.tryEmit(result.clientSubmissionId)
                is FreezeResult.TerminalFailure -> _blankSubmissionFrozen.tryEmit(result.clientSubmissionId)
            }
        }
    }

    private fun ApiException.isTerminalSessionStatusError(): Boolean {
        return code == "INVALID_SESSION_STATUS" || message.contains("VIOLATED", ignoreCase = true)
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
