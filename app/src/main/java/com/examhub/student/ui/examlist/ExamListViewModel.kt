package com.examhub.student.ui.examlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Exam
import com.examhub.student.model.ApiResult
import com.examhub.student.repository.ExamRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExamListViewModel(
    private val examRepository: ExamRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {

    private val _exams = MutableStateFlow<List<Exam>>(emptyList())
    val exams: StateFlow<List<Exam>> = _exams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(gradingType: String) {
        viewModelScope.launch {
            val cached = offlineCacheManager.getCachedExamBasics()
            if (cached.isNotEmpty()) _exams.value = cached

            examRepository.getExams(
                excludeClosed = null,
                gradingType = gradingType.takeIf { it.isNotBlank() }
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cached.isEmpty()
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val exams = result.data.data.map { exam ->
                            Exam(
                                id = exam.id,
                                name = exam.name,
                                subject = exam.subject,
                                className = exam.classInfo?.className ?: exam.examType.orEmpty(),
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
                        _exams.value = exams
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (_exams.value.isEmpty()) _exams.value = cached
                    }
                }
            }
        }
    }
}
