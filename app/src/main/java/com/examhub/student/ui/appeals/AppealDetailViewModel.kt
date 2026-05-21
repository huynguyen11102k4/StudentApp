package com.examhub.student.ui.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Appeal
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.AppealSummaryResponse
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
                        _message.tryEmit(result.exception.message ?: "Không thể tải chi tiết khiếu nại")
                    }
                }
            }
        }
    }

    private fun AppealSummaryResponse.toUiModel(): Appeal {
        return Appeal(
            id = id,
            studentId = student?.id.orEmpty(),
            studentName = student?.name.orEmpty(),
            studentCode = student?.code.orEmpty(),
            examId = exam?.id.orEmpty(),
            examName = exam?.name.orEmpty(),
            subject = exam?.subject.orEmpty(),
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
                "Cau ${item.questionNumber}: ${item.studentMessage.orEmpty().ifBlank { item.status }}"
            }
        )
    }
}
