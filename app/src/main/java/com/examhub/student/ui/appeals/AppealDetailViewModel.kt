package com.examhub.student.ui.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Appeal
import com.examhub.student.extension.replaceTechnicalLabels
import com.examhub.student.extension.toFriendlyAppealItemStatus
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
    private val appealsRepository: AppealsRepository
) : ViewModel() {

    private val _appeal = MutableStateFlow<Appeal?>(null)
    val appeal: StateFlow<Appeal?> = _appeal.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun loadAppeal(appealId: String) {
        viewModelScope.launch {
            appealsRepository.getAppealDetail(appealId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _appeal.value = result.data.toUiModel()
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit((result.exception.message ?: "Không thể tải chi tiết khiếu nại").replaceTechnicalLabels())
                    }
                }
            }
        }
    }

    private fun AppealSummaryResponse.toUiModel(): Appeal {
        val resolvedExam = exam ?: sheet?.exam
        return Appeal(
            id = id,
            studentId = student?.id.orEmpty(),
            studentName = student?.name.orEmpty(),
            studentCode = student?.code.orEmpty(),
            examId = resolvedExam?.id.orEmpty(),
            examName = resolvedExam?.name.orEmpty(),
            subject = resolvedExam?.subject.orEmpty(),
            sheetId = sheet?.id.orEmpty(),
            oldScore = oldScore ?: sheet?.totalScore ?: 0.0,
            newScore = newScore,
            reason = reason.orEmpty(),
            status = status,
            createdAt = createdAt,
            teacherNote = teacherNote,
            processedImageUrl = sheet?.processedImageUrl,
            dewarpedImageUrl = sheet?.dewarpedImageUrl,
            itemMessages = items.orEmpty().joinToString("\n") { item ->
                listOf(
                    "Câu ${item.questionNumber}: ${item.studentMessage.orEmpty().ifBlank { item.status.toFriendlyAppealItemStatus() }}",
                    item.teacherResponse?.takeIf { it.isNotBlank() }?.let { "Phản hồi: $it" }
                ).filterNotNull().joinToString("\n")
            }
        )
    }
}
