package com.omr.scanner.student.ui.lockmode

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.R
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.LockHeartbeatRequest
import com.omr.scanner.student.model.request.LockViolationRequest
import com.omr.scanner.student.repository.LockModeRepository
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
    private val context: Context
) : ViewModel() {
    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _timeExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val timeExpired: SharedFlow<Unit> = _timeExpired.asSharedFlow()
    private val _queuedViolationCount = MutableStateFlow(0)
    val queuedViolationCount: StateFlow<Int> = _queuedViolationCount.asStateFlow()

    private var timerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sessionId: String = ""

    fun start(sessionId: String, initialSeconds: Int) {
        this.sessionId = sessionId
        if (_remainingSeconds.value <= 0) _remainingSeconds.value = initialSeconds.coerceAtLeast(0)
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
                        network = mapOf("type" to "unknown", "strength" to "unknown"),
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

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
