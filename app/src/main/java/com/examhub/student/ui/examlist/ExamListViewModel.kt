package com.examhub.student.ui.examlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Exam
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.exam.MobileExamSummaryResponse
import com.examhub.student.model.response.result.StudentResultSummaryResponse
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExamListViewModel(
    private val examRepository: ExamRepository,
    private val resultsRepository: ResultsRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {

    private val _exams = MutableStateFlow<List<Exam>>(emptyList())
    val exams: StateFlow<List<Exam>> = _exams.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load(gradingType: String) {
        viewModelScope.launch {
            val cached = offlineCacheManager.getCachedExamBasics()

            examRepository.getExams(
                excludeClosed = false,
                gradingType = gradingType.takeIf { it.isNotBlank() }
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cached.isEmpty()
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val resultSummaries = loadResultSummaries()
                        val resultSheetByExamId = resultSummaries.mapNotNull { summary ->
                            val examId = summary.exam?.id?.takeIf { it.isNotBlank() }
                            if (examId == null) null else examId to summary.id
                        }.toMap()
                        result.data.data.forEach { exam ->
                            offlineCacheManager.saveExamClassCode(exam.id, exam.classInfo?.classCode)
                        }
                        val examsFromUpcoming = result.data.data.map { exam ->
                            val resultSheetId = exam.resultId?.takeIf { it.isNotBlank() }
                                ?: resultSheetByExamId[exam.id]
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
                                date = exam.displayTime,
                                resultSheetId = resultSheetId,
                                hasSubmitted = resultSheetId != null || exam.hasSubmittedStatus()
                            )
                        }
                        val missingSubmittedExams = resultSummaries
                            .filter { summary -> summary.exam?.id?.let { id -> examsFromUpcoming.none { it.id == id } } == true }
                            .mapNotNull { it.toSubmittedExam() }
                        val exams = (examsFromUpcoming + missingSubmittedExams)
                            .distinctBy { it.id }
                            .sortedByDescending { it.date }
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

    private suspend fun loadResultSummaries(): List<StudentResultSummaryResponse> {
        return when (val result = resultsRepository.getResults(limit = "100").first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data.data
            else -> emptyList()
        }
    }

    private fun StudentResultSummaryResponse.toSubmittedExam(): Exam? {
        val exam = exam ?: return null
        val examId = exam.id?.takeIf { it.isNotBlank() } ?: return null
        return Exam(
            id = examId,
            name = exam.name.orEmpty().ifBlank { "Bài kiểm tra đã nộp" },
            subject = exam.subject.orEmpty(),
            className = "Kết quả học sinh",
            duration = exam.duration ?: 0,
            questionCount = exam.totalQuestions ?: 0,
            status = "SUBMITTED",
            gradedCount = 1,
            totalStudents = 0,
            isOfflineReady = false,
            date = gradedAt ?: createdAt.orEmpty(),
            resultSheetId = id,
            hasSubmitted = true
        )
    }

    private fun com.examhub.student.model.response.exam.MobileExamSummaryResponse.hasSubmittedStatus(): Boolean {
        val normalized = listOfNotNull(status, submissionStatus)
            .joinToString(" ")
            .uppercase()
        return attemptsUsed?.let { it > 0 } == true ||
            listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any { normalized.contains(it) }
    }
}
