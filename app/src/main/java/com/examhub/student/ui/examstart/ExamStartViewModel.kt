package com.examhub.student.ui.examstart

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.model.response.common.StartExamSessionResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.ActiveExamSession
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ExamStartViewModel(
    private val examRepository: ExamRepository,
    private val lockModeRepository: LockModeRepository,
    private val tokenManager: TokenManager,
    private val offlineCacheManager: OfflineCacheManager,
    private val activeSessionStore: ActiveExamSessionStore,
    private val gson: Gson,
    private val context: Context
) : ViewModel() {
    private val _exam = MutableStateFlow<MobileExamDetailResponse?>(null)
    val exam: StateFlow<MobileExamDetailResponse?> = _exam.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sessionStarted = MutableSharedFlow<ExamStartSessionUiEvent>(extraBufferCapacity = 1)
    val sessionStarted: SharedFlow<ExamStartSessionUiEvent> = _sessionStarted.asSharedFlow()
    private val _sessionResume = MutableSharedFlow<ExamResumeUiEvent>(extraBufferCapacity = 1)
    val sessionResume: SharedFlow<ExamResumeUiEvent> = _sessionResume.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _canStartExam = MutableStateFlow(false)
    val canStartExam: StateFlow<Boolean> = _canStartExam.asStateFlow()

    private var currentExamId: String = ""
    private var currentExamStatus: String = ""
    private var currentGradingType: String = ""
    private var serverCanStartSession: Boolean = false
    private var currentStartTime: String? = null
    private var currentEndTime: String? = null

    fun load(examId: String) {
        currentExamId = examId
        viewModelScope.launch {
            examRepository.getExamDetail(examId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        offlineCacheManager.saveExamClassCode(examId, result.data.classInfo?.classCode)
                        currentExamStatus = result.data.status.orEmpty()
                        currentGradingType = result.data.gradingType.orEmpty()
                        serverCanStartSession = result.data.canStartSession == true &&
                            currentGradingType.equals("STUDENT_SUBMISSION", ignoreCase = true)
                        currentStartTime = result.data.onlineConfig?.startTime
                        currentEndTime = result.data.onlineConfig?.endTime
                        refreshCanStartExam()
                        _exam.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        offlineCacheManager.getCachedExamBasic(examId)?.let { cached ->
                            _exam.value = cached.toExamDetail()
                            currentExamStatus = cached.status
                            currentGradingType = cached.gradingType
                            serverCanStartSession = cached.canStartSession
                            currentStartTime = cached.date
                            currentEndTime = null
                            refreshCanStartExam()
                        } ?: _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_load_failed))
                    }
                }
            }
        }
    }

    fun startSession() {
        if (currentExamId.isBlank() || _isLoading.value) return
        activeSessionStore.get(currentExamId)?.let { active ->
            _sessionResume.tryEmit(active.toResumeEvent())
            return
        }
        if (!_canStartExam.value) {
            _message.tryEmit(context.getString(R.string.exam_start_inactive))
            return
        }
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
                        if (result.exception.code == "SESSION_ACTIVE") {
                            activeSessionStore.get(currentExamId)?.let { active ->
                                _sessionResume.tryEmit(active.toResumeEvent())
                            } ?: _message.tryEmit(context.getString(R.string.exam_start_session_active_remote))
                        } else {
                            _message.tryEmit(result.exception.message ?: context.getString(R.string.exam_start_session_failed))
                        }
                    }
                }
            }
        }
    }

    private suspend fun validateIfNeeded(session: StartExamSessionResponse) {
        if (!session.isLockedMode) {
            _isLoading.value = false
            val omrCodes = resolveOmrCodes(session)
            saveActiveSession(session, omrCodes, session.remainingSeconds)
            _sessionStarted.tryEmit(
                ExamStartSessionUiEvent(
                    session = session,
                    omrCodes = omrCodes,
                    remainingSeconds = session.remainingSeconds
                )
            )
            return
        }

        lockModeRepository.validateSession(
            LockValidateSessionRequest(session.sessionId, tokenManager.getDeviceId())
        ).collect { result ->
            when (result) {
                is ApiResult.Success -> {
                    _isLoading.value = false
                    if (result.data.valid) {
                        val omrCodes = resolveOmrCodes(session)
                        saveActiveSession(session, omrCodes, result.data.remainingSeconds ?: session.remainingSeconds)
                        _sessionStarted.tryEmit(
                            ExamStartSessionUiEvent(
                                session = session,
                                omrCodes = omrCodes,
                                remainingSeconds = result.data.remainingSeconds ?: session.remainingSeconds
                            )
                        )
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

    private fun saveActiveSession(
        session: StartExamSessionResponse,
        omrCodes: LockModeOmrCodes,
        remainingSeconds: Int
    ) {
        activeSessionStore.save(
            ActiveExamSession(
                examId = currentExamId,
                sessionId = session.sessionId,
                endTime = session.endTime,
                remainingSeconds = remainingSeconds,
                savedAtMillis = System.currentTimeMillis(),
                isLockedMode = session.isLockedMode,
                classCode = omrCodes.classCode,
                studentCode = omrCodes.studentCode,
                studentCodeMode = omrCodes.studentCodeMode,
                questionCount = _exam.value?.totalQuestions ?: 0
            )
        )
    }

    private fun resolveOmrCodes(session: StartExamSessionResponse): LockModeOmrCodes {
        val mode = StudentIdentifierMode.from(session.studentCodeType)
            ?: readStudentIdentifierModeFromCache()
        val profile = tokenManager.getCachedProfileJson()
            ?.let { raw -> runCatching { gson.fromJson(raw, UserResponse::class.java) }.getOrNull() }
        val studentCode = when (mode) {
            StudentIdentifierMode.INTERNAL -> listOfNotNull(
                session.studentIdentifierCode,
                profile?.student?.internalId
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            StudentIdentifierMode.EXTERNAL -> session.studentIdentifierCode?.takeIf { it.isNotBlank() }
                ?: profile?.student?.studentCode.orEmpty()
            StudentIdentifierMode.UNKNOWN -> listOfNotNull(
                session.studentIdentifierCode,
                profile?.student?.studentCode,
                profile?.student?.internalId
            ).firstOrNull { it.isNotBlank() }.orEmpty()
        }
        val classCode = resolveClassCode(session)
        offlineCacheManager.saveExamClassCode(currentExamId, classCode)
        return LockModeOmrCodes(
            classCode = classCode,
            studentCode = studentCode,
            studentCodeMode = mode.label
        )
    }

    private fun resolveClassCode(session: StartExamSessionResponse): String {
        session.studentIdentifierClassCode?.takeIf { it.isNotBlank() }?.let { return it }
        session.examClassCode?.takeIf { it.isNotBlank() }?.let { return it }
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

        return ""
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

    private fun refreshCanStartExam() {
        _canStartExam.value = currentGradingType.equals("STUDENT_SUBMISSION", ignoreCase = true) &&
            serverCanStartSession &&
            offlineCacheManager.getTemplate(currentExamId) != null &&
            isWithinExamWindow(currentStartTime, currentEndTime)
    }

    private fun isWithinExamWindow(startTime: String?, endTime: String?): Boolean {
        val now = System.currentTimeMillis()
        val start = parseTimeMillis(startTime)
        val end = parseTimeMillis(endTime)
        if (start != null && now < start) return false
        if (end != null && now > end) return false
        return true
    }

    private fun parseTimeMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
        }
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
            gradingType = gradingType.ifBlank { "STUDENT_SUBMISSION" },
            classInfo = null,
            template = null,
            resultId = resultSheetId,
            canStartSession = canStartSession,
            canSubmit = canSubmit,
            canViewResult = canViewResult,
            resultOnly = resultOnly
        )
    }

    private fun ActiveExamSession.toResumeEvent(): ExamResumeUiEvent {
        return ExamResumeUiEvent(
            examId = examId,
            sessionId = sessionId,
            remainingSeconds = currentRemainingSeconds(),
            isLockedMode = isLockedMode,
            questionCount = questionCount,
            omrCodes = LockModeOmrCodes(
                classCode = classCode,
                studentCode = studentCode,
                studentCodeMode = studentCodeMode
            )
        )
    }
}

data class ExamStartSessionUiEvent(
    val session: StartExamSessionResponse,
    val omrCodes: LockModeOmrCodes,
    val remainingSeconds: Int
)

data class ExamResumeUiEvent(
    val examId: String,
    val sessionId: String,
    val remainingSeconds: Int,
    val isLockedMode: Boolean,
    val questionCount: Int,
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
