package com.omr.scanner.student.ui.examstart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.omr.scanner.student.R
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.LockValidateSessionRequest
import com.omr.scanner.student.model.response.MobileExamDetailResponse
import com.omr.scanner.student.model.response.StartExamSessionResponse
import com.omr.scanner.student.repository.ExamRepository
import com.omr.scanner.student.repository.LockModeRepository
import com.omr.scanner.student.service.OfflineCacheManager
import com.omr.scanner.student.service.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExamStartViewModel(
    private val examRepository: ExamRepository,
    private val lockModeRepository: LockModeRepository,
    private val tokenManager: TokenManager,
    private val offlineCacheManager: OfflineCacheManager,
    private val gson: Gson,
    private val context: Context
) : ViewModel() {
    private val _exam = MutableStateFlow<MobileExamDetailResponse?>(null)
    val exam: StateFlow<MobileExamDetailResponse?> = _exam.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionStarted = MutableSharedFlow<StartExamSessionResponse>(extraBufferCapacity = 1)
    val sessionStarted: SharedFlow<StartExamSessionResponse> = _sessionStarted.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private var currentExamId: String = ""

    fun load(examId: String) {
        currentExamId = examId
        viewModelScope.launch {
            examRepository.getExamDetail(examId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _exam.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_load_failed))
                    }
                }
            }
        }
    }

    fun startSession() {
        if (currentExamId.isBlank() || _isLoading.value) return
        viewModelScope.launch {
            examRepository.startSession(currentExamId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        cacheStartPayload(result.data)
                        validateIfNeeded(result.data)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_session_failed))
                    }
                }
            }
        }
    }

    private suspend fun validateIfNeeded(session: StartExamSessionResponse) {
        if (!session.isLockedMode) {
            _isLoading.value = false
            _sessionStarted.tryEmit(session)
            return
        }

        lockModeRepository.validateSession(
            LockValidateSessionRequest(session.sessionId, tokenManager.getDeviceId())
        ).collect { result ->
            when (result) {
                is ApiResult.Success -> {
                    _isLoading.value = false
                    if (result.data.valid) {
                        _sessionStarted.tryEmit(session)
                    } else {
                        _message.tryEmit(result.data.reason ?: context.getString(R.string.exam_start_lock_invalid))
                    }
                }
                is ApiResult.Error -> {
                    _isLoading.value = false
                    _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_lock_validate_failed))
                }
                else -> Unit
            }
        }
    }

    private fun cacheStartPayload(session: StartExamSessionResponse) {
        val template = session.template ?: error(context.getString(R.string.exam_start_session_failed))
        offlineCacheManager.saveTemplate(
            currentExamId,
            gson.toJson(mapOf("gridConfig" to template.gridConfig))
        )
        offlineCacheManager.markOfflineReady(currentExamId)
    }
}
