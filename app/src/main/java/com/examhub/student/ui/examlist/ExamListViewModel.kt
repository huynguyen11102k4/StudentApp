package com.examhub.student.ui.examlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.examhub.student.model.ApiResult
import com.examhub.student.data.model.Exam
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.util.paging.PageChunk
import com.examhub.student.util.paging.RepositoryPagingSource
import com.examhub.student.util.paging.requirePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class ExamListViewModel(
    private val examRepository: ExamRepository,
    private val resultsRepository: ResultsRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {
    private val search = MutableStateFlow("")
    private val filter = MutableStateFlow(ExamFilter.ALL)
    private val gradingType = MutableStateFlow("")

    val exams: Flow<PagingData<Exam>> = combine(
        search.debounce(250).distinctUntilChanged(),
        filter,
        gradingType
    ) { query, selectedFilter, type -> Triple(query, selectedFilter, type) }
        .flatMapLatest { (query, selectedFilter, type) ->
            Pager(
                config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false),
                pagingSourceFactory = {
                    RepositoryPagingSource { page, limit ->
                        val response = examRepository.getExams(
                            page = page.toString(),
                            limit = limit.toString(),
                            excludeClosed = false,
                            gradingType = type.takeIf(String::isNotBlank),
                            search = query.takeIf(String::isNotBlank)
                        ).requirePage()
                        val resultByExamId = loadResultByExamId()
                        val items = response.data.map { item ->
                            val resultSummary = resultByExamId[item.id]
                            val resultSheetId = item.resultId?.takeIf { it.isNotBlank() } ?: resultSummary?.id
                            offlineCacheManager.saveExamClassCode(item.id, item.classInfo?.classCode)
                            Exam(
                                id = item.id,
                                name = item.name,
                                subject = item.subject,
                                className = item.classInfo?.className ?: item.examType.orEmpty(),
                                duration = item.durationMinutes,
                                questionCount = item.totalQuestions,
                                status = item.status.orEmpty(),
                                gradedCount = if (item.resultId.isNullOrBlank()) 0 else 1,
                                totalStudents = 0,
                                isOfflineReady = offlineCacheManager.getTemplate(item.id) != null,
                                date = item.displayTime,
                                resultSheetId = resultSheetId,
                                hasSubmitted = !resultSheetId.isNullOrBlank() || item.hasSubmittedStatus(),
                                gradingType = item.gradingType.orEmpty(),
                                canStartSession = item.canStartSession == true && item.gradingType.isStudentSubmission(),
                                canSubmit = item.canSubmit == true && item.gradingType.isStudentSubmission(),
                                canViewResult = item.canViewResult == true &&
                                    !resultSheetId.isNullOrBlank() &&
                                    resultSummary?.isPendingResult() != true,
                                resultOnly = item.resultOnly == true || item.gradingType.isTeacherGrading()
                            )
                        }
                        offlineCacheManager.saveExamBasics(items)
                        PageChunk(items, response.meta?.page ?: page, response.meta?.limit ?: limit, response.meta?.total ?: items.size)
                    }
                }
            ).flow.map { data -> data.filter { it.matches(selectedFilter) } }
        }
        .cachedIn(viewModelScope)

    fun configure(type: String, initialFilter: ExamFilter = ExamFilter.ALL) {
        gradingType.value = type
        filter.value = initialFilter
    }

    fun setSearch(query: String) {
        search.value = query.trim()
    }

    fun setFilter(value: ExamFilter) {
        filter.value = value
    }

    private suspend fun loadResultByExamId(): Map<String, com.examhub.student.model.response.result.StudentResultSummaryResponse> {
        return when (val result = resultsRepository.getResults(limit = "100").first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data.data.mapNotNull { summary ->
                val examId = summary.exam?.id?.takeIf { it.isNotBlank() }
                if (examId == null) null else examId to summary
            }.toMap()
            else -> emptyMap()
        }
    }

    private fun com.examhub.student.model.response.exam.MobileExamSummaryResponse.hasSubmittedStatus(): Boolean {
        val normalized = listOfNotNull(status, submissionStatus).joinToString(" ").uppercase()
        return attemptsUsed?.let { it > 0 } == true ||
            listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any(normalized::contains)
    }

    private fun String?.isStudentSubmission(): Boolean =
        equals("STUDENT_SUBMISSION", ignoreCase = true)

    private fun String?.isTeacherGrading(): Boolean =
        equals("TEACHER_GRADING", ignoreCase = true)

    private fun com.examhub.student.model.response.result.StudentResultSummaryResponse.isPendingResult(): Boolean {
        val normalized = resultStatus.orEmpty().uppercase()
        return normalized.contains("PENDING") || normalized.contains("PROCESSING")
    }

    private fun Exam.matches(value: ExamFilter): Boolean = when (value) {
        ExamFilter.ALL -> true
        ExamFilter.READY -> canStartSession && !hasSubmitted && !resultOnly
        ExamFilter.PROCESSING -> hasSubmitted || canViewResult || resultOnly || isSubmittedLikeStatus()
        ExamFilter.CLOSED -> isClosedStatus()
    }

    private fun Exam.isOpenForStudent() = status.isBlank() ||
        listOf("OPEN", "ACTIVE", "PUBLISHED", "STARTED", "READY").any(status.uppercase()::contains)
    private fun Exam.isSubmittedLikeStatus() =
        listOf("SUBMITTED", "PROCESSING", "GRADED", "COMPLETED", "DONE").any(status.uppercase()::contains)
    private fun Exam.isClosedStatus() =
        listOf("CLOSED", "END", "ENDED", "EXPIRED", "LOCKED").any { status.uppercase().contains(it) }

    enum class ExamFilter { ALL, READY, PROCESSING, CLOSED }
}
