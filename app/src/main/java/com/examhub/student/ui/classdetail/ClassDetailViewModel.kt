package com.examhub.student.ui.classdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Exam
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.model.response.exam.MobileExamSummaryResponse
import com.examhub.student.repository.ClassRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ClassDetailViewModel(
    private val classRepository: ClassRepository,
    private val examRepository: ExamRepository,
    private val resultsRepository: ResultsRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {
    private val _classDetail = MutableStateFlow<MobileClassResponse?>(null)
    val classDetail: StateFlow<MobileClassResponse?> = _classDetail.asStateFlow()

    private val _classExams = MutableStateFlow<List<Exam>>(emptyList())
    val classExams: StateFlow<List<Exam>> = _classExams.asStateFlow()
    private var allClassExams: List<Exam> = emptyList()
    private var examSearchQuery: String = ""

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _leaveRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val leaveRequested: SharedFlow<Unit> = _leaveRequested.asSharedFlow()

    fun load(classId: String) {
        if (classId.isBlank()) return
        viewModelScope.launch {
            classRepository.getClassDetail(classId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _classDetail.value = result.data
                        loadClassExams(result.data)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: "Không thể tải chi tiết lớp")
                    }
                }
            }
        }
    }

    fun requestLeaveClass() {
        val classId = _classDetail.value?.classId?.takeIf { it.isNotBlank() }
            ?: _classDetail.value?.classInfo?.id?.takeIf { it.isNotBlank() }
            ?: return
        if (_classDetail.value?.status == "LEAVE_REQUESTED") return
        viewModelScope.launch {
            classRepository.requestLeaveClass(classId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _classDetail.value = result.data
                        _leaveRequested.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: "Không thể gửi yêu cầu rời lớp")
                    }
                }
            }
        }
    }

    private fun loadClassExams(classDetail: MobileClassResponse) {
        viewModelScope.launch {
            examRepository.getExams(limit = "100", excludeClosed = false).collect { result ->
                when (result) {
                    is ApiResult.Loading -> Unit
                    is ApiResult.Success -> {
                        val resultSheetByExamId = loadResultSheetByExamId()
                        allClassExams = result.data.data
                            .filter { it.belongsToClass(classDetail) }
                            .map { it.toExam(resultSheetByExamId) }
                            .sortedByDescending { it.date }
                        applyExamSearch()
                    }
                    is ApiResult.Error -> {
                        _message.tryEmit(result.exception.message ?: "Không thể tải danh sách kỳ thi của lớp")
                    }
                }
            }
        }
    }

    fun setExamSearch(query: String) {
        examSearchQuery = query.trim()
        applyExamSearch()
    }

    private fun applyExamSearch() {
        val query = examSearchQuery.lowercase()
        _classExams.value = if (query.isBlank()) {
            allClassExams
        } else {
            allClassExams.filter { exam ->
                listOf(exam.name, exam.subject, exam.className, exam.status)
                    .any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    private suspend fun loadResultSheetByExamId(): Map<String, String> {
        return when (val result = resultsRepository.getResults(limit = "100").first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data.data.mapNotNull { summary ->
                val examId = summary.exam?.id?.takeIf { it.isNotBlank() }
                if (examId == null) null else examId to summary.id
            }.toMap()
            else -> emptyMap()
        }
    }

    private fun MobileExamSummaryResponse.belongsToClass(classDetail: MobileClassResponse): Boolean {
        val info = classDetail.classInfo
        val classIds = listOf(classDetail.classId, info?.id.orEmpty())
            .filter { it.isNotBlank() }
            .toSet()
        val classCodes = listOf(info?.classCode, info?.joinCode)
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .toSet()
        val classNames = listOf(info?.className)
            .mapNotNull { it?.takeIf(String::isNotBlank) }

        return classInfo?.id?.let { it in classIds } == true ||
            classInfo?.classCode?.let { it in classCodes } == true ||
            classNames.any { it.equals(classInfo?.className, ignoreCase = true) }
    }

    private fun MobileExamSummaryResponse.toExam(resultSheetByExamId: Map<String, String>): Exam {
        val resultSheetId = resultId?.takeIf { it.isNotBlank() } ?: resultSheetByExamId[id]
        return Exam(
            id = id,
            name = name,
            subject = subject,
            className = classInfo?.className ?: examType.orEmpty(),
            duration = durationMinutes,
            questionCount = totalQuestions,
            status = status.orEmpty(),
            gradedCount = count?.answerSheets ?: 0,
            totalStudents = 0,
            isOfflineReady = offlineCacheManager.getTemplate(id) != null,
            date = displayTime,
            resultSheetId = resultSheetId,
            hasSubmitted = resultSheetId != null || hasSubmittedStatus()
        )
    }

    private fun MobileExamSummaryResponse.hasSubmittedStatus(): Boolean {
        val normalized = listOfNotNull(status, submissionStatus)
            .joinToString(" ")
            .uppercase()
        return attemptsUsed?.let { it > 0 } == true ||
            listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any { normalized.contains(it) }
    }
}
