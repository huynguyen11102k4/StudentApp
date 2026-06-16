package com.examhub.student.ui.examdetail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.ExamSubmissionItem
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.response.common.StartExamSessionResponse
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.repository.AppealsRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.ActiveExamSessionStore
import com.examhub.student.data.local.model.ActiveExamSession
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.extension.toFriendlyExamStatus
import com.examhub.student.util.extension.toFriendlyExamType
import com.examhub.student.util.extension.toFriendlyGradingType
import com.examhub.student.util.helper.parseUserProfileJson
import com.examhub.student.security.SecurePermitStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ExamDetailViewModel(
    private val examRepository: ExamRepository,
    private val appealsRepository: AppealsRepository,
    private val lockModeRepository: LockModeRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val tokenManager: TokenManager,
    private val activeSessionStore: ActiveExamSessionStore,
    private val gson: Gson,
    private val context: Context,
    private val securePermitStore: SecurePermitStore
) : ViewModel() {

    private val _examName = MutableStateFlow("")
    val examName: StateFlow<String> = _examName.asStateFlow()
    private val _subject = MutableStateFlow("")
    val subject: StateFlow<String> = _subject.asStateFlow()
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()
    private val _questionCount = MutableStateFlow(0)
    val questionCount: StateFlow<Int> = _questionCount.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _examType = MutableStateFlow("")
    val examType: StateFlow<String> = _examType.asStateFlow()
    private val _gradingType = MutableStateFlow("")
    val gradingType: StateFlow<String> = _gradingType.asStateFlow()
    private val _templateName = MutableStateFlow("")
    val templateName: StateFlow<String> = _templateName.asStateFlow()
    private val _examWindowNotice = MutableStateFlow("")
    val examWindowNotice: StateFlow<String> = _examWindowNotice.asStateFlow()
    private val _progressText = MutableStateFlow("")
    val progressText: StateFlow<String> = _progressText.asStateFlow()
    private val _submissions = MutableStateFlow<List<ExamSubmissionItem>>(emptyList())
    val submissions: StateFlow<List<ExamSubmissionItem>> = _submissions.asStateFlow()
    private val _gradingProgress = MutableStateFlow(0)
    val gradingProgress: StateFlow<Int> = _gradingProgress.asStateFlow()
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()
    private val _downloadStep = MutableStateFlow("")
    val downloadStep: StateFlow<String> = _downloadStep.asStateFlow()
    private val _isOfflineReady = MutableStateFlow(false)
    val isOfflineReady: StateFlow<Boolean> = _isOfflineReady.asStateFlow()
    private val _pendingAppealCount = MutableStateFlow(0)
    val pendingAppealCount: StateFlow<Int> = _pendingAppealCount.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isStartingSession = MutableStateFlow(false)
    val isStartingSession: StateFlow<Boolean> = _isStartingSession.asStateFlow()
    private val _canStartExam = MutableStateFlow(false)
    val canStartExam: StateFlow<Boolean> = _canStartExam.asStateFlow()
    private val _canViewResult = MutableStateFlow(false)
    val canViewResult: StateFlow<Boolean> = _canViewResult.asStateFlow()
    private val _resultOnly = MutableStateFlow(false)
    val resultOnly: StateFlow<Boolean> = _resultOnly.asStateFlow()
    private val _resultId = MutableStateFlow("")
    val resultId: StateFlow<String> = _resultId.asStateFlow()
    private val _isExamExpired = MutableStateFlow(false)
    val isExamExpired: StateFlow<Boolean> = _isExamExpired.asStateFlow()
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    private val _sessionStarted = MutableSharedFlow<ExamDetailSessionUiEvent>(extraBufferCapacity = 1)
    val sessionStarted: SharedFlow<ExamDetailSessionUiEvent> = _sessionStarted.asSharedFlow()

    private var currentExamId: String = ""
    private var currentExamStatus: String = ""
    private var currentGradingType: String = ""
    private var serverCanStartSession: Boolean = false
    private var currentStartTime: String? = null
    private var currentEndTime: String? = null

    fun loadExam(examId: String) {
        currentExamId = examId
        _isOfflineReady.value = offlineCacheManager.isOfflineReady(examId)
        viewModelScope.launch {
            examRepository.getExamDetail(examId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        bindExam(result.data)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        offlineCacheManager.getCachedExamBasic(examId)?.let(::applyCachedExam)
                            ?: _toastMessage.tryEmit(
                                (result.exception.message ?: context.getString(R.string.exam_detail_loading_failed)).replaceTechnicalLabels()
                            )
                    }
                }
            }
        }
        loadPendingAppeals(examId)
    }

    private fun bindExam(exam: MobileExamDetailResponse) {
        currentExamStatus = exam.status.orEmpty()
        currentGradingType = exam.gradingType.orEmpty()
        serverCanStartSession = exam.canStartSession == true &&
            currentGradingType.equals("STUDENT_SUBMISSION", ignoreCase = true)
        currentStartTime = exam.onlineConfig?.startTime
        currentEndTime = exam.onlineConfig?.endTime
        _resultId.value = exam.resultId.orEmpty()
        _canViewResult.value = exam.canViewResult == true && !exam.resultId.isNullOrBlank()
        _resultOnly.value = exam.resultOnly == true ||
            currentGradingType.equals("TEACHER_GRADING", ignoreCase = true)
        _examName.value = exam.name
        _subject.value = exam.subject
        _duration.value = exam.durationMinutes
        _questionCount.value = exam.totalQuestions
        _status.value = exam.status.toFriendlyExamStatus()
        _examType.value = listOfNotNull(
            exam.classInfo?.className?.takeIf { it.isNotBlank() },
            exam.examType.toFriendlyExamType().takeIf { it.isNotBlank() }
        ).joinToString(context.getString(R.string.common_separator_dot)).ifBlank { context.getString(R.string.exam_detail_default_exam_type) }
        _gradingType.value = exam.gradingType.toFriendlyGradingType().ifBlank { context.getString(R.string.exam_detail_default_grading_type) }
        _templateName.value = exam.template?.name ?: context.getString(R.string.exam_detail_data_ready_on_start)
        _progressText.value = context.getString(R.string.exam_detail_ready_to_submit)
        refreshExamWindowNotice()
        refreshCanStartExam()
        _isExamExpired.value = currentExamStatus.equals("END", ignoreCase = true) ||
            (currentExamStatus.equals("ACTIVE", ignoreCase = true) && isAfterEndTime(currentEndTime))
        offlineCacheManager.saveExamClassCode(exam.id, exam.classInfo?.classCode)
        offlineCacheManager.saveExamBasic(exam.toCachedExam())
    }

    private fun loadPendingAppeals(examId: String) {
        viewModelScope.launch {
            appealsRepository.getAppeals(status = "PENDING", examId = examId).collect { result ->
                if (result is ApiResult.Success) {
                    _pendingAppealCount.value = result.data.meta?.total ?: result.data.data.size
                }
            }
        }
    }

    fun downloadGradingData() {
        if (currentExamId.isBlank()) return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStep.value = context.getString(R.string.exam_detail_downloading_data)
            val templateResult = examRepository.getExamTemplate(currentExamId)
                .first { it !is ApiResult.Loading }
            if (templateResult is ApiResult.Error) {
                _downloadStep.value = ""
                _isDownloading.value = false
                _toastMessage.tryEmit(
                    (templateResult.exception.message ?: context.getString(R.string.exam_detail_download_failed))
                        .replaceTechnicalLabels()
                )
                return@launch
            }
            templateResult as ApiResult.Success
            offlineCacheManager.saveTemplate(currentExamId, gson.toJson(templateResult.data.toCacheMap()))

            val metadataResult = examRepository.getQuestionMetadata(currentExamId)
                .first { it !is ApiResult.Loading }
            if (metadataResult is ApiResult.Success) {
                offlineCacheManager.saveQuestionMetadata(currentExamId, gson.toJson(metadataResult.data))
            }

            offlineCacheManager.markOfflineReady(currentExamId)
            _isOfflineReady.value = offlineCacheManager.isOfflineReady(currentExamId)
            refreshCanStartExam()
            _downloadStep.value = ""
            _isDownloading.value = false
            if (metadataResult is ApiResult.Error) {
                _toastMessage.tryEmit(
                    (metadataResult.exception.message ?: context.getString(R.string.exam_detail_download_failed))
                        .replaceTechnicalLabels()
                )
            } else {
                _toastMessage.tryEmit(context.getString(R.string.exam_detail_download_success))
            }
        }
    }

    fun startSession() {
        if (currentExamId.isBlank() || _isStartingSession.value) return
        if (!_canStartExam.value) {
            _toastMessage.tryEmit(context.getString(R.string.exam_detail_start_not_allowed))
            return
        }
        if (!currentExamStatus.equals("ACTIVE", ignoreCase = true)) {
            _toastMessage.tryEmit(context.getString(R.string.exam_detail_start_only_active))
            return
        }
        activeSessionStore.get(currentExamId)?.let {
            _toastMessage.tryEmit(context.getString(R.string.exam_start_resume_local_session))
            _sessionStarted.tryEmit(it.toSessionEvent())
            return
        }
        viewModelScope.launch {
            examRepository.startSession(currentExamId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isStartingSession.value = true
                    is ApiResult.Success -> {
                        val session = result.data
                        cacheStartPayload(session)
                        val omrCodes = resolveOmrCodes(session)
                        saveActiveSession(session, omrCodes, session.remainingSeconds)
                        if (session.isLockedMode) {
                            validateLockedSession(session, omrCodes)
                        } else {
                            _isStartingSession.value = false
                            _sessionStarted.tryEmit(session.toSessionEvent(omrCodes, session.remainingSeconds))
                        }
                    }
                    is ApiResult.Error -> {
                        _isStartingSession.value = false
                        if (result.exception.code == "SESSION_ACTIVE") {
                            _toastMessage.tryEmit(context.getString(R.string.exam_detail_remote_session_active))
                        } else {
                            _toastMessage.tryEmit((result.exception.message ?: context.getString(R.string.exam_detail_start_session_failed)).replaceTechnicalLabels())
                        }
                    }
                }
            }
        }
    }

    private suspend fun validateLockedSession(session: StartExamSessionResponse, omrCodes: LockModeOmrCodes) {
        lockModeRepository.validateSession(
            LockValidateSessionRequest(
                sessionId = session.sessionId,
                deviceId = tokenManager.getDeviceId()
            )
        ).collect { lockResult ->
            when (lockResult) {
                is ApiResult.Success -> {
                    _isStartingSession.value = false
                    if (lockResult.data.valid) {
                        _sessionStarted.tryEmit(session.toSessionEvent(omrCodes, lockResult.data.remainingSeconds ?: session.remainingSeconds))
                    } else {
                        _toastMessage.tryEmit((lockResult.data.reason ?: context.getString(R.string.exam_detail_lock_invalid)).replaceTechnicalLabels())
                    }
                }
                is ApiResult.Error -> {
                    _isStartingSession.value = false
                    _toastMessage.tryEmit((lockResult.exception.message ?: context.getString(R.string.exam_detail_lock_validate_failed)).replaceTechnicalLabels())
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
        _isOfflineReady.value = true
        refreshCanStartExam()
    }

    private fun saveActiveSession(
        session: StartExamSessionResponse,
        omrCodes: LockModeOmrCodes,
        remainingSeconds: Int
    ) {
        session.offlineSubmission?.permit
            ?.takeIf { it.isNotBlank() }
            ?.let { securePermitStore.save(session.sessionId, it) }
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
                questionCount = _questionCount.value,
                deviceId = tokenManager.getDeviceId(),
                offlineDeadlineAt = session.offlineSubmission?.deadlineAt,
                offlineSyncDeadlineAt = session.offlineSubmission?.syncDeadlineAt,
                requiresClientSubmissionId = session.offlineSubmission?.requiresClientSubmissionId == true
            )
        )
    }

    private fun resolveOmrCodes(session: StartExamSessionResponse): LockModeOmrCodes {
        val mode = StudentIdentifierMode.from(session.studentCodeType)
            ?: readStudentIdentifierModeFromCache()
        val profile = tokenManager.getCachedProfileJson()
            ?.let(gson::parseUserProfileJson)
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
        offlineCacheManager.getExamClassCode(currentExamId)?.takeIf { it.isNotBlank() }?.let { return it }
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

    private fun applyCachedExam(exam: Exam) {
        currentExamStatus = exam.status
        currentGradingType = exam.gradingType
        serverCanStartSession = exam.canStartSession
        currentStartTime = exam.date
        currentEndTime = null
        _resultId.value = exam.resultSheetId.orEmpty()
        _canViewResult.value = exam.canViewResult && !exam.resultSheetId.isNullOrBlank()
        _resultOnly.value = exam.resultOnly
        _examName.value = exam.name
        _subject.value = exam.subject
        _duration.value = exam.duration
        _questionCount.value = exam.questionCount
        _status.value = exam.status.toFriendlyExamStatus()
        _examType.value = ""
        _templateName.value = if (offlineCacheManager.isOfflineReady(exam.id)) {
            context.getString(R.string.exam_detail_data_ready)
        } else {
            context.getString(R.string.exam_detail_data_missing)
        }
        _isOfflineReady.value = offlineCacheManager.isOfflineReady(exam.id)
        refreshExamWindowNotice()
        refreshCanStartExam()
        _isExamExpired.value = currentExamStatus.equals("END", ignoreCase = true) ||
            (currentExamStatus.equals("ACTIVE", ignoreCase = true) && isAfterEndTime(currentEndTime))
    }

    private fun refreshCanStartExam() {
        _canStartExam.value = currentGradingType.equals("STUDENT_SUBMISSION", ignoreCase = true) &&
            serverCanStartSession &&
            _isOfflineReady.value &&
            isWithinExamWindow(currentStartTime, currentEndTime)
    }

    private fun refreshExamWindowNotice() {
        if (_resultOnly.value && !_canViewResult.value) {
            _examWindowNotice.value = context.getString(R.string.exam_detail_waiting_result)
            return
        }
        if (_resultOnly.value) {
            _examWindowNotice.value = context.getString(R.string.exam_detail_result_only_notice)
            return
        }
        _examWindowNotice.value = if (
            currentExamStatus.equals("END", ignoreCase = true) ||
            (currentExamStatus.equals("ACTIVE", ignoreCase = true) && isAfterEndTime(currentEndTime))
        ) {
            context.getString(R.string.exam_detail_after_end_notice)
        } else {
            ""
        }
    }

    private fun isWithinExamWindow(startTime: String?, endTime: String?): Boolean {
        val now = System.currentTimeMillis()
        val start = parseTimeMillis(startTime)
        val end = parseTimeMillis(endTime)
        if (start != null && now < start) return false
        if (end != null && now > end) return false
        return true
    }

    private fun isAfterEndTime(endTime: String?): Boolean {
        val end = parseTimeMillis(endTime) ?: return false
        return System.currentTimeMillis() > end
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

    private fun MobileExamDetailResponse.toCachedExam(): Exam {
        return Exam(
            id = id,
            name = name,
            subject = subject,
            className = classInfo?.className ?: examType.orEmpty(),
            duration = durationMinutes,
            questionCount = totalQuestions,
            status = status.orEmpty(),
            gradedCount = 0,
            totalStudents = 0,
            isOfflineReady = offlineCacheManager.isOfflineReady(id),
            date = onlineConfig?.startTime.orEmpty(),
            resultSheetId = resultId,
            gradingType = gradingType.orEmpty(),
            canStartSession = canStartSession == true && gradingType.equals("STUDENT_SUBMISSION", ignoreCase = true),
            canSubmit = canSubmit == true && gradingType.equals("STUDENT_SUBMISSION", ignoreCase = true),
            canViewResult = canViewResult == true && !resultId.isNullOrBlank(),
            resultOnly = resultOnly == true || gradingType.equals("TEACHER_GRADING", ignoreCase = true)
        )
    }

    private fun com.examhub.student.model.response.template.OmrTemplateResponse.toCacheMap(): Map<String, Any?> {
        return buildMap {
            gridConfig?.let { put("gridConfig", it) }
            anchorPoints?.let { put("anchor_points", it) }
            studentCodeType?.takeIf { it.isNotBlank() }?.let {
                put("student_code_type", it)
            }
            identificationMode?.takeIf { it.isNotBlank() }?.let {
                put("identification_mode", it)
            }
        }
    }

    private fun StartExamSessionResponse.toSessionEvent(
        omrCodes: LockModeOmrCodes,
        remainingSeconds: Int
    ): ExamDetailSessionUiEvent {
        return ExamDetailSessionUiEvent(
            session = this,
            omrCodes = omrCodes,
            remainingSeconds = remainingSeconds,
            questionCount = _questionCount.value
        )
    }

    private fun ActiveExamSession.toSessionEvent(): ExamDetailSessionUiEvent {
        return ExamDetailSessionUiEvent(
            session = StartExamSessionResponse(
                legacySessionId = sessionId,
                legacyEndTime = endTime,
                legacyRemainingSeconds = currentRemainingSeconds(),
                legacyIsLockedMode = isLockedMode
            ),
            omrCodes = LockModeOmrCodes(
                classCode = classCode,
                studentCode = studentCode,
                studentCodeMode = studentCodeMode
            ),
            remainingSeconds = currentRemainingSeconds(),
            questionCount = questionCount
        )
    }
}

data class ExamDetailSessionUiEvent(
    val session: StartExamSessionResponse,
    val omrCodes: LockModeOmrCodes,
    val remainingSeconds: Int,
    val questionCount: Int
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
