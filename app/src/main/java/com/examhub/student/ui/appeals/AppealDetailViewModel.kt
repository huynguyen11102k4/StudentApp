package com.examhub.student.ui.appeals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.data.model.Appeal
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.repository.AppealsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppealDetailViewModel(
    private val appealsRepository: AppealsRepository,
    private val context: Context
) : ViewModel() {

    private val _appeal = MutableStateFlow<Appeal?>(null)
    val appeal: StateFlow<Appeal?> = _appeal.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun loadAppeal(appealId: String, fallbackStudentName: String = "", fallbackStudentCode: String = "") {
        viewModelScope.launch {
            appealsRepository.getAppealDetail(appealId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _appeal.value = result.data.toUiModel(fallbackStudentName, fallbackStudentCode)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit((result.exception.message ?: context.getString(R.string.appeal_detail_load_failed)).replaceTechnicalLabels())
                    }
                }
            }
        }
    }

    private fun AppealSummaryResponse.toUiModel(fallbackStudentName: String, fallbackStudentCode: String): Appeal {
        val resolvedExam = exam ?: sheet?.exam
        val resolvedStudent = student ?: sheet?.student
        return Appeal(
            id = id,
            studentId = resolvedStudent?.id.orEmpty(),
            studentName = resolvedStudent?.name.orEmpty().ifBlank { fallbackStudentName },
            studentCode = resolvedStudent?.code.orEmpty().ifBlank { fallbackStudentCode },
            examId = resolvedExam?.id.orEmpty(),
            examName = resolvedExam?.name.orEmpty(),
            subject = resolvedExam?.subject.orEmpty(),
            sheetId = sheet?.id.orEmpty(),
            oldScore = oldScore ?: sheet?.totalScore ?: 0.0,
            newScore = newScore,
            reason = reason.orEmpty(),
            status = status,
            createdAt = createdAt,
            teacherNote = teacherNote
        )
    }
}
