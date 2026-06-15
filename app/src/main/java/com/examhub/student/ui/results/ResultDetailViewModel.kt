package com.examhub.student.ui.results

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.appeal.StudentAppealRequest
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.model.response.result.StudentResultExamResponse
import com.examhub.student.repository.AppealsRepository
import com.examhub.student.repository.ExamRepository
import com.examhub.student.repository.ResultsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ResultDetailViewModel(
    private val resultsRepository: ResultsRepository,
    private val appealsRepository: AppealsRepository,
    private val examRepository: ExamRepository,
    private val context: Context
) : ViewModel() {
    private val _result = MutableStateFlow<StudentResultDetailResponse?>(null)
    val result: StateFlow<StudentResultDetailResponse?> = _result.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _appealCreated = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val appealCreated: SharedFlow<String> = _appealCreated.asSharedFlow()
    private val _appealCount = MutableStateFlow(0)
    val appealCount: StateFlow<Int> = _appealCount.asStateFlow()
    private val _appeals = MutableStateFlow<List<Appeal>>(emptyList())
    val appeals: StateFlow<List<Appeal>> = _appeals.asStateFlow()
    private val _appealsLoaded = MutableStateFlow(false)
    val appealsLoaded: StateFlow<Boolean> = _appealsLoaded.asStateFlow()
    private val _isResultPending = MutableStateFlow(false)
    val isResultPending: StateFlow<Boolean> = _isResultPending.asStateFlow()
    private val _resultUnavailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resultUnavailable: SharedFlow<Unit> = _resultUnavailable.asSharedFlow()

    private var fallbackExamStatus: String = ""

    fun loadResult(sheetId: String, examStatus: String = "") {
        fallbackExamStatus = examStatus
        if (sheetId.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_pending_message))
            _resultUnavailable.tryEmit(Unit)
            return
        }
        viewModelScope.launch {
            resultsRepository.getResultDetail(sheetId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val resolvedResult = result.data.resolveExamStatus()
                        _result.value = resolvedResult
                        _isResultPending.value = resolvedResult.isPending()
                        if (!resolvedResult.isPending()) {
                            loadAppeals(resolvedResult)
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.result_detail_load_failed))
                        _resultUnavailable.tryEmit(Unit)
                    }
                }
            }
        }
    }

    fun createAppeal(reason: String) {
        val initialResult = _result.value
        val sheetId = initialResult?.id.orEmpty()
        if (sheetId.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_missing_sheet))
            return
        }
        val normalizedReason = reason.trim()
        if (normalizedReason.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_reason_required))
            return
        }
        viewModelScope.launch {
            val refreshed = resultsRepository.getResultDetail(sheetId).first { it !is ApiResult.Loading }
            val result = when (refreshed) {
                is ApiResult.Success -> {
                    val resolvedResult = refreshed.data.resolveExamStatus()
                    _result.value = resolvedResult
                    resolvedResult
                }
                else -> initialResult
            }
            if (result == null) {
                _message.tryEmit(context.getString(R.string.result_detail_missing_sheet))
                return@launch
            }
            if (!result.exam?.status.isAppealOpenStatus()) {
                _message.tryEmit(context.getString(R.string.result_detail_appeal_closed_exam))
                return@launch
            }
            if (!result.resultStatus.equals("GRADED", ignoreCase = true)) {
                _message.tryEmit(context.getString(R.string.result_detail_pending_message))
                return@launch
            }
            val pendingCount = countPendingAppealsForResult(result)
            _appealCount.value = pendingCount
            if (pendingCount > 0) {
                _message.tryEmit(context.getString(R.string.result_detail_appeal_pending_exists))
                return@launch
            }
            appealsRepository.createAppeal(StudentAppealRequest(sheetId, normalizedReason)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _appealCount.value = (_appealCount.value + 1).coerceAtLeast(1)
                        _result.value?.let(::loadAppeals)
                        _appealCreated.tryEmit(result.data.appealId)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.result_detail_create_appeal_failed))
                    }
                }
            }
        }
    }

    private fun loadAppeals(result: StudentResultDetailResponse) {
        val sheetId = result.id.orEmpty()
        if (sheetId.isBlank()) {
            _appealCount.value = 0
            _appeals.value = emptyList()
            _appealsLoaded.value = true
            return
        }
        viewModelScope.launch {
            _appealsLoaded.value = false
            val response = appealsRepository.getAppeals(
                examId = result.exam?.id,
                page = "1",
                limit = "100"
            ).first { it !is ApiResult.Loading }
            if (response is ApiResult.Success) {
                val items = response.data.data
                    .filter { it.sheet?.id == sheetId }
                    .map { it.toUiModel(result) }
                    .sortedByDescending(Appeal::createdAt)
                _appeals.value = items
                _appealCount.value = items.count { it.status.equals("PENDING", ignoreCase = true) }
            }
            _appealsLoaded.value = true
        }
    }

    private suspend fun countPendingAppealsForResult(result: StudentResultDetailResponse): Int {
        val sheetId = result.id.orEmpty()
        if (sheetId.isBlank()) return 0
        val response = appealsRepository.getAppeals(status = "PENDING", examId = result.exam?.id)
            .first { it !is ApiResult.Loading }
        return if (response is ApiResult.Success) {
            response.data.data.count { it.sheet?.id == sheetId }
        } else {
            _appealCount.value
        }
    }

    private fun StudentResultDetailResponse.isPending(): Boolean {
        return resultStatus.equals("PENDING", ignoreCase = true) && id.isNullOrBlank()
    }

    private suspend fun StudentResultDetailResponse.resolveExamStatus(): StudentResultDetailResponse {
        if (!exam?.status.isNullOrBlank()) return this
        val examStatus = exam?.id
            ?.takeIf { it.isNotBlank() }
            ?.let { examId ->
                when (val result = examRepository.getExamDetail(examId).first { it !is ApiResult.Loading }) {
                    is ApiResult.Success -> result.data.status
                    else -> null
                }
            }
            ?.takeIf { it.isNotBlank() }
            ?: fallbackExamStatus
        if (examStatus.isBlank()) return this
        return copy(exam = (exam ?: StudentResultExamResponse()).copy(status = examStatus))
    }

    private fun String?.isAppealOpenStatus(): Boolean {
        return equals("END", ignoreCase = true)
    }

    private fun AppealSummaryResponse.toUiModel(result: StudentResultDetailResponse): Appeal {
        val resolvedExam = exam ?: sheet?.exam
        val resolvedStudent = student ?: sheet?.student
        return Appeal(
            id = id,
            studentId = resolvedStudent?.id.orEmpty(),
            studentName = resolvedStudent?.name.orEmpty().ifBlank { result.displayStudentName().orEmpty() },
            studentCode = resolvedStudent?.code.orEmpty().ifBlank { result.displayStudentCode().orEmpty() },
            examId = resolvedExam?.id.orEmpty().ifBlank { result.exam?.id.orEmpty() },
            examName = resolvedExam?.name.orEmpty().ifBlank { result.exam?.name.orEmpty() },
            subject = resolvedExam?.subject.orEmpty().ifBlank { result.exam?.subject.orEmpty() },
            sheetId = sheet?.id.orEmpty(),
            oldScore = oldScore ?: sheet?.totalScore ?: result.totalScore ?: 0.0,
            newScore = newScore,
            reason = reason.orEmpty(),
            status = status,
            createdAt = createdAt,
            teacherNote = teacherNote
        )
    }
}
