package com.examhub.student.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.model.response.exam.MobileExamSummaryResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.repository.ClassRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.first
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
    private val resultsRepository: ResultsRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val context: Context
) : ViewModel() {

    private val _recentExams = MutableStateFlow<List<Exam>>(emptyList())
    val recentExams: StateFlow<List<Exam>> = _recentExams.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _profile = MutableStateFlow<UserResponse?>(null)
    val profile: StateFlow<UserResponse?> = _profile.asStateFlow()

    private val _classCount = MutableStateFlow(0)
    val classCount: StateFlow<Int> = _classCount.asStateFlow()

    fun loadDashboard() {
        // Load from cache for instant display â€” but only show if not stale
        val cachedExams = offlineCacheManager.getCachedExamBasics()
        if (cachedExams.isNotEmpty()) {
            _recentExams.value = cachedExams.take(5)
            _isLoading.value = false
        } else {
            _isLoading.value = true
        }

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
                        val studentCount = cls.studentCount ?: info?.studentCount ?: 0
                        SchoolClass(
                            id = cls.classId.ifBlank { info?.id ?: cls.id },
                            name = info?.className.orEmpty(),
                            subject = info?.subject ?: listOf(info?.grade, info?.schoolYear)
                                .filterNotNull()
                                .filter { it.isNotBlank() }
                                .joinToString(" - "),
                            classCode = cls.resolvedInternalClassCode(),
                            joinCode = info?.joinCode.orEmpty(),
                            studentCount = studentCount,
                            hasOfflineData = false
                        )
                    })
                }
            }
        }

        viewModelScope.launch {
            examRepository.getExams(excludeClosed = false).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cachedExams.isEmpty()
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
                        val exams = result.data.data.map { exam ->
                            val resultSheetId = exam.resultId?.takeIf { it.isNotBlank() }
                                ?: resultSheetByExamId[exam.id]
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
                                date = exam.displayTime,
                                resultSheetId = resultSheetId,
                                hasSubmitted = resultSheetId != null || exam.hasSubmittedStatus(),
                                gradingType = exam.gradingType.orEmpty(),
                                canStartSession = exam.canStartSession == true && exam.gradingType.isStudentSubmission(),
                                canSubmit = exam.canSubmit == true && exam.gradingType.isStudentSubmission(),
                                canViewResult = exam.canViewResult == true && resultSheetId != null,
                                resultOnly = exam.resultOnly == true || exam.gradingType.isTeacherGrading()
                            )
                        }
                        val missingSubmittedExams = resultSummaries
                            .filter { summary -> summary.exam?.id?.let { id -> exams.none { it.id == id } } == true }
                            .mapNotNull { it.toSubmittedExam() }
                        val allExams = (exams + missingSubmittedExams)
                            .distinctBy { it.id }
                            .sortedByDescending { it.date }
                        offlineCacheManager.saveExamBasics(allExams)
                        _recentExams.value = allExams.take(5)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (_recentExams.value.isEmpty()) {
                            _recentExams.value = cachedExams
                        }
                        _toastMessage.tryEmit(result.exception.message ?: context.getString(R.string.dashboard_load_failed))
                    }
                }
            }
        }
    }

    fun syncNow() {
        loadDashboard()
    }

    private fun com.examhub.student.model.response.classroom.MobileClassResponse.resolvedInternalClassCode(): String {
        return classInfo?.classCode.orEmpty()
    }

    private suspend fun loadResultSummaries(): List<com.examhub.student.model.response.result.StudentResultSummaryResponse> {
        return when (val result = resultsRepository.getResults(limit = "100").first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data.data
            else -> emptyList()
        }
    }

    private fun com.examhub.student.model.response.result.StudentResultSummaryResponse.toSubmittedExam(): Exam? {
        val exam = exam ?: return null
        val examId = exam.id?.takeIf { it.isNotBlank() } ?: return null
        return Exam(
            id = examId,
            name = exam.name.orEmpty().ifBlank { context.getString(R.string.dashboard_submitted_exam_name) },
            subject = exam.subject.orEmpty(),
            className = context.getString(R.string.dashboard_student_result_class),
            duration = exam.duration ?: 0,
            questionCount = exam.totalQuestions ?: 0,
            status = "SUBMITTED",
            gradedCount = 1,
            totalStudents = 0,
            isOfflineReady = false,
            date = gradedAt ?: createdAt.orEmpty(),
            resultSheetId = id,
            hasSubmitted = true,
            canViewResult = true,
            resultOnly = true
        )
    }

    private fun com.examhub.student.model.response.exam.MobileExamSummaryResponse.hasSubmittedStatus(): Boolean {
        val normalized = listOfNotNull(status, submissionStatus)
            .joinToString(" ")
            .uppercase()
        return attemptsUsed?.let { it > 0 } == true ||
            listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any { normalized.contains(it) }
    }

    private fun String?.isStudentSubmission(): Boolean =
        equals("STUDENT_SUBMISSION", ignoreCase = true)

    private fun String?.isTeacherGrading(): Boolean =
        equals("TEACHER_GRADING", ignoreCase = true)
}
