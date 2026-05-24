package com.examhub.student.ui.examdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.ExamSubmissionItem
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.model.response.common.StartExamSessionResponse
import com.examhub.student.model.response.template.OmrTemplateResponse
import com.examhub.student.repository.AppealsRepository
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

class ExamDetailViewModel(
    private val examRepository: ExamRepository,
    private val appealsRepository: AppealsRepository,
    private val lockModeRepository: LockModeRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val tokenManager: TokenManager,
    private val gson: Gson
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
    private val _gradingType = MutableStateFlow("STUDENT_SUBMISSION")
    val gradingType: StateFlow<String> = _gradingType.asStateFlow()
    private val _templateName = MutableStateFlow("")
    val templateName: StateFlow<String> = _templateName.asStateFlow()
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
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()
    private val _sessionStarted = MutableSharedFlow<StartExamSessionResponse>(extraBufferCapacity = 1)
    val sessionStarted: SharedFlow<StartExamSessionResponse> = _sessionStarted.asSharedFlow()

    private var currentExamId: String = ""

    fun loadExam(examId: String) {
        currentExamId = examId
        _isOfflineReady.value = offlineCacheManager.getTemplate(examId) != null
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
                            ?: _toastMessage.tryEmit(result.exception.message ?: "Không thể tải thông tin kỳ thi")
                    }
                }
            }
        }
        loadPendingAppeals(examId)
    }

    private fun bindExam(exam: MobileExamDetailResponse) {
        _examName.value = exam.name
        _subject.value = exam.subject
        _duration.value = exam.durationMinutes
        _questionCount.value = exam.totalQuestions
        _status.value = exam.status?.takeIf { it.isNotBlank() } ?: "Đang mở"
        _examType.value = listOfNotNull(
            exam.classInfo?.className?.takeIf { it.isNotBlank() },
            exam.examType?.takeIf { it.isNotBlank() }
        ).joinToString(" • ").ifBlank { "Bài thi học sinh nộp" }
        _gradingType.value = when (exam.gradingType) {
            "STUDENT_SUBMISSION", null, "" -> "Học sinh nộp bài"
            else -> exam.gradingType
        }
        _templateName.value = exam.template?.name ?: "Mẫu OMR đã sẵn sàng khi bắt đầu bài"
        _progressText.value = "Sẵn sàng nộp bài"
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
            _downloadStep.value = "Đang tải mẫu OMR..."
            examRepository.getExamTemplate(currentExamId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        offlineCacheManager.saveTemplate(currentExamId, gson.toJson(result.data.toCacheMap()))
                        _isOfflineReady.value = offlineCacheManager.isOfflineReady(currentExamId)
                        _downloadStep.value = ""
                        _isDownloading.value = false
                        _toastMessage.tryEmit("Đã tải mẫu OMR. Dữ liệu phiên thi sẽ tải khi bắt đầu bài.")
                    }
                    is ApiResult.Error -> {
                        _isDownloading.value = false
                        _toastMessage.tryEmit(result.exception.message ?: "Không thể tải mẫu OMR")
                    }
                    else -> Unit
                }
            }
        }
    }

    fun startSession() {
        if (currentExamId.isBlank() || _isStartingSession.value) return
        viewModelScope.launch {
            examRepository.startSession(currentExamId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isStartingSession.value = true
                    is ApiResult.Success -> {
                        val session = result.data
                        cacheStartPayload(session)
                        if (session.isLockedMode) {
                            validateLockedSession(session)
                        } else {
                            _isStartingSession.value = false
                            _sessionStarted.tryEmit(session)
                        }
                    }
                    is ApiResult.Error -> {
                        _isStartingSession.value = false
                        _toastMessage.tryEmit(result.exception.message ?: "Không thể bắt đầu phiên làm bài")
                    }
                }
            }
        }
    }

    private suspend fun validateLockedSession(session: StartExamSessionResponse) {
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
                        _sessionStarted.tryEmit(session)
                    } else {
                        _toastMessage.tryEmit(lockResult.data.reason ?: "Phien lam bai khong hop le tren thiet bi nay")
                    }
                }
                is ApiResult.Error -> {
                    _isStartingSession.value = false
                    _toastMessage.tryEmit(lockResult.exception.message ?: "Không thể xác thực lock mode")
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
    }

    private fun applyCachedExam(exam: Exam) {
        _examName.value = exam.name
        _subject.value = exam.subject
        _duration.value = exam.duration
        _questionCount.value = exam.questionCount
        _status.value = exam.status
        _examType.value = ""
        _templateName.value = if (offlineCacheManager.getTemplate(exam.id) != null) "Mẫu OMR đã sẵn sàng" else "Chưa có mẫu OMR"
        _isOfflineReady.value = offlineCacheManager.getTemplate(exam.id) != null
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
            isOfflineReady = offlineCacheManager.getTemplate(id) != null,
            date = onlineConfig?.startTime.orEmpty()
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
}
