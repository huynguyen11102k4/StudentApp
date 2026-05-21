package com.examhub.student.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.repository.ClassRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.service.OfflineCacheManager
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
                    val classes = result.data.data
                    _classCount.value = result.data.meta?.total ?: classes.size
                    offlineCacheManager.saveClassBasics(classes.map { cls ->
                        val info = cls.classInfo
                        SchoolClass(
                            id = cls.classId.ifBlank { info?.id ?: cls.id },
                            name = info?.className.orEmpty(),
                            subject = info?.subject ?: listOf(info?.grade, info?.schoolYear)
                                .filterNotNull()
                                .filter { it.isNotBlank() }
                                .joinToString(" - "),
                            classCode = cls.internalId.orEmpty(),
                            joinCode = info?.joinCode.orEmpty(),
                            studentCount = info?.count?.classMembers
                                ?: info?.count?.students
                                ?: info?.count?.members
                                ?: 0,
                            hasOfflineData = false
                        )
                    })
                }
            }
        }

        viewModelScope.launch {
            examRepository.getExams(excludeClosed = null).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cachedExams.isEmpty()
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val exams = result.data.data.map { exam ->
                            Exam(
                                id = exam.id,
                                name = exam.name,
                                subject = exam.subject,
                                className = exam.classInfo?.className ?: exam.gradingType ?: exam.examType.orEmpty(),
                                duration = exam.durationMinutes,
                                questionCount = exam.totalQuestions,
                                status = exam.status.orEmpty(),
                                gradedCount = exam.count?.answerSheets ?: 0,
                                totalStudents = 0,
                                isOfflineReady = offlineCacheManager.getTemplate(exam.id) != null,
                                date = exam.onlineConfig?.startTime ?: exam.onlineConfig?.endTime.orEmpty()
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
