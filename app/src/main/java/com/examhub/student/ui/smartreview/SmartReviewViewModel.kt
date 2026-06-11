package com.examhub.student.ui.smartreview

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Base64
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.submission.PresignSubmissionImageRequest
import com.examhub.student.model.request.submission.IdZoneResultRequest
import com.examhub.student.model.request.submission.StudentAnswerRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.data.model.Answer
import com.examhub.student.omr.OmrReviewStore
import com.examhub.student.repository.StudentSubmissionRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.model.response.submission.PresignSubmissionImageResponse
import com.examhub.student.model.response.submission.StudentSubmitResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SmartReviewViewModel(
    private val omrReviewStore: OmrReviewStore,
    private val studentSubmissionRepository: StudentSubmissionRepository,
    private val activeSessionStore: ActiveExamSessionStore,
    private val examRepository: ExamRepository,
    private val context: Context
) : ViewModel() {

    private val _filteredAnswers = MutableStateFlow<List<Answer>>(emptyList())
    val filteredAnswers: StateFlow<List<Answer>> = _filteredAnswers.asStateFlow()

    private val _savedSuccess = MutableSharedFlow<StudentSubmitResponse>(extraBufferCapacity = 1)
    val savedSuccess: SharedFlow<StudentSubmitResponse> = _savedSuccess.asSharedFlow()
    private val _blankSubmissionFinished = MutableSharedFlow<StudentSubmitResponse?>(extraBufferCapacity = 1)
    val blankSubmissionFinished: SharedFlow<StudentSubmitResponse?> = _blankSubmissionFinished.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _reviewState = MutableStateFlow(ReviewUiState())
    val reviewState: StateFlow<ReviewUiState> = _reviewState.asStateFlow()

    private val allAnswers = mutableListOf<Answer>()
    private var currentFilter = "all"
    private var currentExamId: String = ""
    private var currentSessionId: String = ""
    private var currentStudentId: String = ""
    private var currentClassCode: String? = null
    private var currentExamCode: String? = null
    private var currentStudentIdEnabled: Boolean = false
    private var currentClassCodeEnabled: Boolean = false
    private var currentExamCodeEnabled: Boolean = false
    private var currentIdOk: Boolean = false
    private var currentIdError: String? = null
    private var currentStudentName: String? = null
    private var currentRawImageBase64: String = ""
    private var currentDewarpedImageBase64: String = ""
    private var currentDebugImageBase64: String = ""
    private var currentLaplacianVariance: Float = 0f
    private var currentMeanBrightness: Float = 0f
    private var currentWarnings: List<String> = emptyList()

    fun loadReviewData(submissionId: String) {
        omrReviewStore.consume()?.let { result ->
            currentExamId = result.examId
            currentSessionId = result.sessionId
            currentStudentId = result.studentId
            currentClassCode = result.classCode
            currentExamCode = result.examCode
            currentStudentIdEnabled = result.studentIdEnabled
            currentClassCodeEnabled = result.classCodeEnabled
            currentExamCodeEnabled = result.examCodeEnabled
            currentIdOk = result.idOk
            currentIdError = result.idError
            currentStudentName = result.studentName
            currentRawImageBase64 = result.rawImageBase64
            currentDewarpedImageBase64 = result.dewarpedImageBase64
            currentDebugImageBase64 = result.debugImageBase64
            currentLaplacianVariance = result.laplacianVariance
            currentMeanBrightness = result.meanBrightness
            currentWarnings = result.warnings
            setAnswers(result.answers)
            updateReviewState()
        }
    }

    fun setAnswers(answers: List<Answer>) {
        allAnswers.clear()
        allAnswers.addAll(answers)
        updateReviewState()
        applyFilter()
    }

    fun filterAnswers(filter: String) {
        currentFilter = filter
        applyFilter()
    }

    fun editAnswer(answer: Answer) {
        val index = allAnswers.indexOfFirst { it.questionNo == answer.questionNo }
        if (index >= 0) {
            allAnswers[index] = answer
            updateReviewState()
            applyFilter()
        }
    }

    fun saveResults(totalScore: Double, examId: String, studentId: String) {
        val sessionId = currentSessionId.ifBlank { _reviewState.value.sessionId }
        if (sessionId.isBlank()) {
            _errorMessage.tryEmit(context.getString(R.string.smart_review_missing_session))
            return
        }
        val rawImageBase64 = currentRawImageBase64.ifBlank { currentDebugImageBase64 }
        val dewarpedImageBase64 = currentDewarpedImageBase64.ifBlank { currentDebugImageBase64 }
        val processedImageBase64 = currentDebugImageBase64
        if (rawImageBase64.isBlank() || dewarpedImageBase64.isBlank() || processedImageBase64.isBlank()) {
            _errorMessage.tryEmit(context.getString(R.string.smart_review_missing_omr_image))
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            if (!canSubmitCurrentExam()) {
                _isLoading.value = false
                _errorMessage.tryEmit(context.getString(R.string.smart_review_exam_closed_no_submit))
                return@launch
            }
            val rawImageBytes = decodeBase64Image(rawImageBase64) ?: run {
                _isLoading.value = false
                _errorMessage.tryEmit(context.getString(R.string.smart_review_invalid_raw_image))
                return@launch
            }
            val dewarpedImageBytes = decodeBase64Image(dewarpedImageBase64) ?: run {
                _isLoading.value = false
                _errorMessage.tryEmit(context.getString(R.string.smart_review_invalid_dewarped_image))
                return@launch
            }
            val processedImageBytes = decodeBase64Image(processedImageBase64) ?: run {
                _isLoading.value = false
                _errorMessage.tryEmit(context.getString(R.string.smart_review_invalid_review_image))
                return@launch
            }
            val fileType = "image/jpeg"
            val rawImageUrl = uploadSubmissionImage(sessionId, rawImageBytes, fileType, "raw") ?: return@launch
            val dewarpedImageUrl = uploadSubmissionImage(sessionId, dewarpedImageBytes, fileType, "dewarped") ?: return@launch
            val processedImageUrl = uploadSubmissionImage(sessionId, processedImageBytes, fileType, "processed") ?: return@launch

            val submit = studentSubmissionRepository.submit(
                sessionId,
                StudentSubmitRequest(
                    rawImageUrl = rawImageUrl,
                    dewarpedImageUrl = dewarpedImageUrl,
                    processedImageUrl = processedImageUrl,
                    scannedStudentId = if (currentStudentIdEnabled) currentStudentId.ifBlank { null } else null,
                    scannedClassCode = if (currentClassCodeEnabled) currentClassCode else null,
                    scannedExamCode = if (currentExamCodeEnabled) currentExamCode else null,
                    idResult = buildIdResultRequest(),
                    studentAnswers = allAnswers.map {
                        StudentAnswerRequest(
                            questionNumber = it.questionNo,
                            answer = it.studentAnswer?.ifBlank { null }
                        )
                    },
                    capturedAt = nowIso(),
                    imageQualityScore = calculateImageQualityScore(),
                    qualityFeedback = buildQualityFeedback()
                )
            ).first { it !is ApiResult.Loading }

            _isLoading.value = false
            when (submit) {
                is ApiResult.Success -> {
                    activeSessionStore.clear(currentExamId)
                    activeSessionStore.clearBySessionId(sessionId)
                    _savedSuccess.tryEmit(submit.data)
                }
                is ApiResult.Error -> _errorMessage.tryEmit(submit.exception.message ?: context.getString(R.string.smart_review_submit_failed))
                else -> Unit
            }
        }
    }

    private suspend fun uploadSubmissionImage(
        sessionId: String,
        imageBytes: ByteArray,
        fileType: String,
        imageKind: String
    ): String? {
        val presign = studentSubmissionRepository
            .presignImage(
                sessionId,
                PresignSubmissionImageRequest(
                    fileSize = imageBytes.size.toLong(),
                    fileType = fileType,
                    imageKind = imageKind
                )
            )
            .first { it !is ApiResult.Loading }

        if (presign !is ApiResult.Success<PresignSubmissionImageResponse>) {
            _isLoading.value = false
            _errorMessage.tryEmit((presign as? ApiResult.Error)?.exception?.message ?: context.getString(R.string.smart_review_presign_failed_format, imageKind))
            return null
        }

        val uploadResult = studentSubmissionRepository
            .uploadImage(presign.data.uploadUrl, imageBytes, fileType)
            .first { it !is ApiResult.Loading }
        if (uploadResult is ApiResult.Error) {
            _isLoading.value = false
            _errorMessage.tryEmit(uploadResult.exception.message ?: context.getString(R.string.smart_review_upload_failed_format, imageKind))
            return null
        }

        return presign.data.fileUrl
    }

    private fun buildIdResultRequest(): IdZoneResultRequest? {
        if (!currentStudentIdEnabled && !currentClassCodeEnabled && !currentExamCodeEnabled) return null
        return IdZoneResultRequest(
            studentId = if (currentStudentIdEnabled) currentStudentId.ifBlank { null } else null,
            classCode = if (currentClassCodeEnabled) currentClassCode else null,
            examCode = if (currentExamCodeEnabled) currentExamCode else null,
            idOk = currentIdOk,
            idError = currentIdError
        )
    }

    private fun decodeBase64Image(base64: String): ByteArray? {
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }

    private fun calculateImageQualityScore(): Int {
        val blurScore = when {
            currentLaplacianVariance <= 0f -> 0
            currentLaplacianVariance >= 200f -> 45
            else -> ((currentLaplacianVariance / 200f) * 45f).toInt()
        }
        val brightnessScore = when {
            currentMeanBrightness <= 0f -> 0
            currentMeanBrightness in 80f..220f -> 35
            currentMeanBrightness < 80f -> ((currentMeanBrightness / 80f) * 35f).toInt()
            else -> (((255f - currentMeanBrightness).coerceAtLeast(0f) / 35f) * 35f).toInt().coerceIn(0, 35)
        }
        val idScore = if (currentIdOk || !currentStudentIdEnabled && !currentClassCodeEnabled && !currentExamCodeEnabled) 10 else 0
        val warningPenalty = (currentWarnings.size * 5).coerceAtMost(20)
        return (blurScore + brightnessScore + idScore + 10 - warningPenalty).coerceIn(0, 100)
    }

    private fun buildQualityFeedback(): Map<String, String> {
        return buildMap {
            put("laplacian_variance", "%.2f".format(Locale.US, currentLaplacianVariance))
            put("mean_brightness", "%.2f".format(Locale.US, currentMeanBrightness))
            put("id_ok", currentIdOk.toString())
            put("warnings", currentWarnings.joinToString(",").ifBlank { "none" })
        }
    }

    private suspend fun canSubmitCurrentExam(): Boolean {
        if (currentExamId.isBlank()) return false
        val result = examRepository.getExamDetail(currentExamId).first { it !is ApiResult.Loading }
        return when (result) {
            is ApiResult.Success -> result.data.status.equals("ACTIVE", ignoreCase = true)
            is ApiResult.Error -> true
            else -> true
        }
    }

    fun setFallbackSession(sessionId: String) {
        if (currentSessionId.isBlank()) currentSessionId = sessionId
        updateReviewState()
    }

    private fun applyFilter() {
        _filteredAnswers.value = when (currentFilter) {
            "empty" -> allAnswers.filter { it.status == "empty" }
            else -> allAnswers
        }
    }

    private fun updateReviewState() {
        _reviewState.value = ReviewUiState(
            studentId = currentStudentId,
            studentName = currentStudentName,
            classCode = currentClassCode,
            examCode = currentExamCode,
            examId = currentExamId,
            sessionId = currentSessionId,
            total = allAnswers.size,
            empty = allAnswers.count { it.status == "empty" },
            debugImageBase64 = currentDebugImageBase64,
            hasOmrResult = allAnswers.isNotEmpty(),
            hasOmrWarning = currentWarnings.isNotEmpty()
        )
    }

    fun submitBlankOnTimeout(examId: String, questionCount: Int) {
        val sessionId = currentSessionId.ifBlank { _reviewState.value.sessionId }
        if (sessionId.isBlank()) {
            _blankSubmissionFinished.tryEmit(null)
            return
        }
        val resolvedExamId = currentExamId.ifBlank { examId }
        viewModelScope.launch {
            _isLoading.value = true
            val submit = studentSubmissionRepository.submit(
                sessionId,
                StudentSubmitRequest(
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
                    capturedAt = nowIso(),
                    imageQualityScore = 0,
                    qualityFeedback = mapOf(
                        "auto_submitted" to "true",
                        "reason" to "time_expired_no_scan",
                        "exam_id" to resolvedExamId
                    )
                )
            ).first { it !is ApiResult.Loading }

            _isLoading.value = false
            val submission = if (submit is ApiResult.Success) {
                activeSessionStore.clear(resolvedExamId)
                activeSessionStore.clearBySessionId(sessionId)
                submit.data
            } else {
                null
            }
            _blankSubmissionFinished.tryEmit(submission)
        }
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}

data class ReviewUiState(
    val score: Double? = null,
    val studentId: String = "",
    val studentName: String? = null,
    val classCode: String? = null,
    val examCode: String? = null,
    val examId: String = "",
    val sessionId: String = "",
    val total: Int = 0,
    val empty: Int = 0,
    val debugImageBase64: String = "",
    val hasOmrResult: Boolean = false,
    val hasOmrWarning: Boolean = false
)
