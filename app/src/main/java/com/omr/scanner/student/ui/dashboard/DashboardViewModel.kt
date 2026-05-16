package com.omr.scanner.student.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.data.model.Exam
import com.omr.scanner.student.data.model.SchoolClass
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.UserResponse
import com.omr.scanner.student.repository.AuthRepository
import com.omr.scanner.student.repository.ClassRepository
import com.omr.scanner.student.repository.ExamRepository
import com.omr.scanner.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val examRepository: ExamRepository,
    private val authRepository: AuthRepository,
    private val classRepository: ClassRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {

    private val _recentExams = MutableStateFlow<List<Exam>>(emptyList())
    val recentExams: StateFlow<List<Exam>> = _recentExams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _profile = MutableStateFlow<UserResponse?>(null)
    val profile: StateFlow<UserResponse?> = _profile.asStateFlow()

    private val _classCount = MutableStateFlow(0)
    val classCount: StateFlow<Int> = _classCount.asStateFlow()

    fun loadDashboard() {
        val cachedExams = offlineCacheManager.getCachedExamBasics()
        if (cachedExams.isNotEmpty()) _recentExams.value = cachedExams.take(5)

        val cachedClasses = offlineCacheManager.getCachedClassBasics()
        if (cachedClasses.isNotEmpty()) _classCount.value = cachedClasses.size

        viewModelScope.launch {
            authRepository.getMe().collect { result ->
                if (result is ApiResult.Success) _profile.value = result.data
            }
        }

        viewModelScope.launch {
            classRepository.getClasses(status = "active").collect { result ->
                if (result is ApiResult.Success) {
                    _classCount.value = result.data.meta.total
                    offlineCacheManager.saveClassBasics(result.data.data.map { cls ->
                        SchoolClass(
                            id = cls.classId.ifBlank { cls.classInfo?.id ?: cls.id },
                            name = cls.className.ifBlank { cls.classInfo?.className.orEmpty() },
                            subject = cls.subject ?: cls.classInfo?.subject ?: listOf(cls.grade, cls.schoolYear)
                                .filter { it.isNotBlank() }
                                .joinToString(" - "),
                            classCode = cls.internalId.orEmpty(),
                            joinCode = cls.joinCode.orEmpty(),
                            studentCount = cls.count?.classMembers ?: cls.studentCount,
                            hasOfflineData = false
                        )
                    })
                }
            }
        }

        viewModelScope.launch {
            examRepository.getExams(excludeClosed = true).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cachedExams.isEmpty()
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val exams = result.data.data.map { exam ->
                            Exam(
                                id = exam.id,
                                name = exam.name,
                                subject = exam.subject,
                                className = exam.gradingType ?: exam.examType.orEmpty(),
                                duration = exam.durationMinutes,
                                questionCount = exam.totalQuestions,
                                status = exam.status,
                                gradedCount = exam.count?.answerSheets ?: 0,
                                totalStudents = 0,
                                isOfflineReady = offlineCacheManager.getTemplate(exam.id) != null,
                                date = exam.startTime ?: exam.endTime.orEmpty()
                            )
                        }
                        offlineCacheManager.saveExamBasics(exams)
                        _recentExams.value = exams.take(5)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (_recentExams.value.isEmpty()) {
                            _recentExams.value = cachedExams
                        }
                        _toastMessage.tryEmit(result.exception.message ?: "Không thể tải dữ liệu trang chủ")
                    }
                }
            }
        }
    }

    fun syncNow() {
        loadDashboard()
    }
}
