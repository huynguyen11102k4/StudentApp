package com.examhub.student.ui.examstart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.LockValidateSessionRequest
import com.examhub.student.model.response.MobileExamDetailResponse
import com.examhub.student.model.response.StartExamSessionResponse
import com.examhub.student.model.response.UserResponse
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
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

    private val _sessionStarted = MutableSharedFlow<ExamStartSessionUiEvent>(extraBufferCapacity = 1)
    val sessionStarted: SharedFlow<ExamStartSessionUiEvent> = _sessionStarted.asSharedFlow()

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
                        offlineCacheManager.saveExamClassCode(examId, result.data.classInfo?.classCode)
                        _exam.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        offlineCacheManager.getCachedExamBasic(examId)?.let { cached ->
                            _exam.value = cached.toExamDetail()
                        } ?: _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_load_failed))
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
            _sessionStarted.tryEmit(ExamStartSessionUiEvent(session, resolveOmrCodes(session)))
            return
        }

        lockModeRepository.validateSession(
            LockValidateSessionRequest(session.sessionId, tokenManager.getDeviceId())
        ).collect { result ->
            when (result) {
                is ApiResult.Success -> {
                    _isLoading.value = false
                    if (result.data.valid) {
                        _sessionStarted.tryEmit(ExamStartSessionUiEvent(session, resolveOmrCodes(session)))
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
        val template = session.template ?: return
        val gridConfig = template.gridConfig ?: return
        offlineCacheManager.saveTemplate(
            currentExamId,
            gson.toJson(
                buildMap<String, Any?> {
                    put("gridConfig", gridConfig)
                    session.studentCodeType?.takeIf { it.isNotBlank() }?.let {
                        put("student_code_type", it)
                    }
                }
            )
        )
        offlineCacheManager.markOfflineReady(currentExamId)
    }

    private fun resolveOmrCodes(session: StartExamSessionResponse): LockModeOmrCodes {
        val mode = StudentIdentifierMode.from(session.studentCodeType)
            ?: readStudentIdentifierModeFromCache()
        val profile = tokenManager.getCachedProfileJson()
            ?.let { raw -> runCatching { gson.fromJson(raw, UserResponse::class.java) }.getOrNull() }
        val studentCode = when (mode) {
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
        val classCode = resolveClassCode()
        offlineCacheManager.saveExamClassCode(currentExamId, classCode)
        return LockModeOmrCodes(
            classCode = classCode,
            studentCode = studentCode,
            studentCodeMode = mode.label
        )
    }

    private fun resolveClassCode(): String {
        _exam.value?.classInfo?.classCode?.takeIf { it.isNotBlank() }?.let { return it }
        offlineCacheManager.getExamClassCode(currentExamId)?.takeIf { it.isNotBlank() }?.let { return it }

        val examClassId = _exam.value?.classInfo?.id.orEmpty()
        val cachedClasses = offlineCacheManager.getCachedClassBasics()
        if (examClassId.isNotBlank()) {
            cachedClasses.firstOrNull { it.id == examClassId }
                ?.classCode
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        return cachedClasses
            .takeIf { it.size == 1 }
            ?.firstOrNull()
            ?.classCode
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    private fun readStudentIdentifierModeFromCache(): StudentIdentifierMode {
        val rawTemplate = offlineCacheManager.getTemplate(currentExamId) ?: return StudentIdentifierMode.UNKNOWN
        return runCatching {
            val root = org.json.JSONObject(rawTemplate)
            listOf(
                root.optString("student_code_type"),
                root.optString("identification_mode")
            ).firstNotNullOfOrNull { StudentIdentifierMode.from(it) } ?: StudentIdentifierMode.UNKNOWN
        }.getOrDefault(StudentIdentifierMode.UNKNOWN)
    }

    private fun Exam.toExamDetail(): MobileExamDetailResponse {
        return MobileExamDetailResponse(
            id = id,
            name = name,
            subject = subject,
            totalQuestions = questionCount,
            durationMinutes = duration,
            status = status,
            examType = null,
            gradingType = "STUDENT_SUBMISSION",
            classInfo = null,
            template = null
        )
    }
}

data class ExamStartSessionUiEvent(
    val session: StartExamSessionResponse,
    val omrCodes: LockModeOmrCodes
)

data class LockModeOmrCodes(
    val classCode: String = "",
    val studentCode: String = "",
    val studentCodeMode: String = "UNKNOWN"
)

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
