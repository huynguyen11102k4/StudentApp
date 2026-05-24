package com.examhub.student.ui.lockmode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.LockModeRepository
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
    private val context: Context,
    private val offlineCacheManager: OfflineCacheManager,
    private val tokenManager: TokenManager,
    private val gson: Gson
) : ViewModel() {
    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _timeExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timeExpired: SharedFlow<Unit> = _timeExpired.asSharedFlow()
    private val _queuedViolationCount = MutableStateFlow(0)
    val queuedViolationCount: StateFlow<Int> = _queuedViolationCount.asStateFlow()
    private val _omrCodes = MutableStateFlow(LockModeOmrCodes())
    val omrCodes: StateFlow<LockModeOmrCodes> = _omrCodes.asStateFlow()

    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sessionId: String = ""

    fun start(sessionId: String, examId: String, initialSeconds: Int, argCodes: LockModeOmrCodes = LockModeOmrCodes()) {
        this.sessionId = sessionId
        if (_remainingSeconds.value <= 0) _remainingSeconds.value = initialSeconds.coerceAtLeast(0)
        loadOmrCodes(examId, argCodes)
        startTimer()
        startHeartbeat()
    }

    fun logViolation(type: String, evidence: Map<String, Any?> = emptyMap()) {
        val id = sessionId.takeIf { it.isNotBlank() } ?: return
        val request = LockViolationRequest(
            sessionId = id,
            violationType = type,
            occurredAt = nowIso(),
            evidenceData = evidence
        )
        lockModeRepository.queueViolation(request)
        _queuedViolationCount.value = lockModeRepository.queuedViolationCount()
        flushViolations()
    }

    fun flushViolations() {
        viewModelScope.launch {
            lockModeRepository.flushQueuedViolations().collect { result ->
                when (result) {
                    is ApiResult.Success -> _queuedViolationCount.value = lockModeRepository.queuedViolationCount()
                    is ApiResult.Error -> _message.tryEmit(result.exception.message ?: context.getString(R.string.lock_mode_violation_queued))
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
            while (true) {
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
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.lock_mode_heartbeat_failed))
                    }
                }
                delay(30_000)
            }
        }
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
            ?: offlineCacheManager.getCachedClassBasics()
                .takeIf { it.size == 1 }
                ?.firstOrNull()
                ?.classCode
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
