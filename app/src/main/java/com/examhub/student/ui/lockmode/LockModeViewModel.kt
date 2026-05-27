package com.examhub.student.ui.lockmode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.examhub.student.R
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.request.submission.IdZoneResultRequest
import com.examhub.student.model.request.submission.StudentAnswerRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.repository.StudentSubmissionRepository
import com.examhub.student.service.NetworkStatusProvider
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

class LockModeViewModel(
    private val lockModeRepository: LockModeRepository,
    private val studentSubmissionRepository: StudentSubmissionRepository,
    private val context: Context,
    private val offlineCacheManager: OfflineCacheManager,
    private val tokenManager: TokenManager,
    private val gson: Gson
) : ViewModel() {
    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()
    private val _timeExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timeExpired: SharedFlow<Unit> = _timeExpired.asSharedFlow()
    private val _blankSubmissionFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val blankSubmissionFinished: SharedFlow<Unit> = _blankSubmissionFinished.asSharedFlow()
    private val _omrCodes = MutableStateFlow(LockModeOmrCodes())
    val omrCodes: StateFlow<LockModeOmrCodes> = _omrCodes.asStateFlow()

    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sessionId: String = ""
    private var examId: String = ""
    private var questionCount: Int = 0
    private var blankSubmitted = false
    private var stopped = false

    fun start(
        sessionId: String,
        examId: String,
        initialSeconds: Int,
        questionCount: Int,
        argCodes: LockModeOmrCodes = LockModeOmrCodes()
    ) {
        this.sessionId = sessionId
        this.examId = examId
        this.questionCount = questionCount.coerceAtLeast(0)
        stopped = false
        if (_remainingSeconds.value <= 0) _remainingSeconds.value = initialSeconds.coerceAtLeast(0)
        loadOmrCodes(examId, argCodes)
        startTimer()
        startHeartbeat()
    }

    fun logViolation(type: String, evidence: Map<String, Any?> = emptyMap()) {
        if (stopped) return
        val id = sessionId.takeIf { it.isNotBlank() } ?: return
        val request = LockViolationRequest(
            sessionId = id,
            violationType = type,
            occurredAt = nowIso(),
            evidenceData = evidence
        )
        lockModeRepository.queueViolation(request)
        flushViolations()
    }

    fun flushViolations() {
        if (stopped) return
        viewModelScope.launch {
            lockModeRepository.flushQueuedViolations().collect { result ->
                when (result) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> Unit
                    else -> Unit
                }
            }
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1_000)
                _remainingSeconds.value = (_remainingSeconds.value - 1).coerceAtLeast(0)
            }
            _timeExpired.tryEmit(Unit)
        }
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true || sessionId.isBlank()) return
        heartbeatJob = viewModelScope.launch {
            while (!stopped) {
                lockModeRepository.heartbeat(
                    sessionId,
                    LockHeartbeatRequest(
                        network = NetworkStatusProvider.currentNetwork(context),
                        appInForeground = true
                    )
                ).collect { result ->
                    if (result is ApiResult.Success) {
                        _remainingSeconds.value = result.data.remainingSeconds
                        flushViolations()
                    } else if (result is ApiResult.Error) {
                        if (result.exception.isTerminalSessionStatusError()) {
                            stopHeartbeat()
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
        timerJob?.cancel()
        timerJob = null
        stopHeartbeat()
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun ApiException.isTerminalSessionStatusError(): Boolean {
        return code == "INVALID_SESSION_STATUS" || message.contains("VIOLATED", ignoreCase = true)
    }

    fun submitBlankOnTimeout() {
        val id = sessionId.takeIf { it.isNotBlank() } ?: return
        if (blankSubmitted) {
            _blankSubmissionFinished.tryEmit(Unit)
            return
        }
        blankSubmitted = true
        viewModelScope.launch {
            val count = resolveQuestionCount()
            val codes = _omrCodes.value
            val localExam = offlineCacheManager.getCachedExamBasic(examId)
            if (localExam != null) {
                offlineCacheManager.saveExamBasic(localExam.copy(status = "SUBMITTED", hasSubmitted = true))
            }
            studentSubmissionRepository.submit(
                id,
                StudentSubmitRequest(
                    rawImageUrl = null,
                    dewarpedImageUrl = null,
                    processedImageUrl = null,
                    scannedStudentId = codes.studentCode.takeIf { it.isNotBlank() },
                    scannedClassCode = codes.classCode.takeIf { it.isNotBlank() },
                    scannedExamCode = null,
                    idResult = IdZoneResultRequest(
                        studentId = codes.studentCode.takeIf { it.isNotBlank() },
                        classCode = codes.classCode.takeIf { it.isNotBlank() },
                        examCode = null,
                        idOk = codes.studentCode.isNotBlank() || codes.classCode.isNotBlank(),
                        idError = "time_expired_no_scan"
                    ),
                    studentAnswers = (1..count).map { questionNo ->
                        StudentAnswerRequest(questionNumber = questionNo, answer = null)
                    },
                    capturedAt = nowIso(),
                    imageQualityScore = 0,
                    qualityFeedback = mapOf(
                        "auto_submitted" to "true",
                        "reason" to "time_expired_no_scan",
                        "exam_id" to examId
                    )
                )
            ).collect { result ->
                if (result !is ApiResult.Loading) {
                    _blankSubmissionFinished.tryEmit(Unit)
                }
            }
        }
    }

    private fun resolveQuestionCount(): Int {
        if (questionCount > 0) return questionCount
        val rawTemplate = offlineCacheManager.getTemplate(examId) ?: return 0
        return runCatching {
            val root = org.json.JSONObject(rawTemplate)
            val grid = root.optJSONObject("gridConfig") ?: root.optJSONObject("grid_config")
            val zones = grid?.optJSONArray("answer_zones") ?: grid?.optJSONArray("answerZones")
            var maxQuestion = 0
            if (zones != null) {
                for (i in 0 until zones.length()) {
                    val zone = zones.optJSONObject(i) ?: continue
                    maxQuestion = maxOf(
                        maxQuestion,
                        zone.optInt("end_number", zone.optInt("endNumber", 0))
                    )
                }
            }
            maxQuestion
        }.getOrDefault(0)
    }

    private fun loadOmrCodes(examId: String, argCodes: LockModeOmrCodes) {
        val mode = readStudentIdentifierMode(examId)
        val profile = tokenManager.getCachedProfileJson()
            ?.let { raw -> runCatching { gson.fromJson(raw, UserResponse::class.java) }.getOrNull() }
        val cachedStudentCode = when (mode) {
            StudentIdentifierMode.INTERNAL -> listOfNotNull(
                profile?.student?.internalId,
                profile?.student?.id
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            StudentIdentifierMode.EXTERNAL -> profile?.student?.studentCode.orEmpty()
            StudentIdentifierMode.UNKNOWN -> listOfNotNull(
                profile?.student?.studentCode,
                profile?.student?.internalId,
                profile?.student?.id
            ).firstOrNull { it.isNotBlank() }.orEmpty()
        }
        val cachedClassCode = offlineCacheManager.getExamClassCode(examId)
            .orEmpty()
        _omrCodes.value = LockModeOmrCodes(
            classCode = argCodes.classCode.ifBlank { cachedClassCode },
            studentCode = argCodes.studentCode.ifBlank { cachedStudentCode },
            studentCodeMode = argCodes.studentCodeMode.ifBlank { mode.label }
        )
    }

    private fun readStudentIdentifierMode(examId: String): StudentIdentifierMode {
        val rawTemplate = offlineCacheManager.getTemplate(examId) ?: return StudentIdentifierMode.UNKNOWN
        return runCatching {
            val root = org.json.JSONObject(rawTemplate)
            listOf(
                root.optString("student_code_type"),
                root.optString("identification_mode")
            ).firstNotNullOfOrNull { StudentIdentifierMode.from(it) } ?: StudentIdentifierMode.UNKNOWN
        }.getOrDefault(StudentIdentifierMode.UNKNOWN)
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }

    private enum class StudentIdentifierMode(val label: String) {
        INTERNAL("INTERNAL"),
        EXTERNAL("EXTERNAL"),
        UNKNOWN("UNKNOWN");

        companion object {
            fun from(value: String?): StudentIdentifierMode? {
                return when (value?.trim()?.uppercase()) {
                    "INTERNAL" -> INTERNAL
                    "EXTERNAL" -> EXTERNAL
                    else -> null
                }
            }
        }
    }
}

data class LockModeOmrCodes(
    val classCode: String = "",
    val studentCode: String = "",
    val studentCodeMode: String = "UNKNOWN"
)
