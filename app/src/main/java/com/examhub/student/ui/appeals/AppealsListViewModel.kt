package com.examhub.student.ui.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.Appeal
import com.examhub.student.util.extension.toFriendlyAppealItemStatus
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.repository.AppealsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppealsListViewModel(
    private val appealsRepository: AppealsRepository
) : ViewModel() {

    private val _appeals = MutableStateFlow<List<Appeal>>(emptyList())
    val appeals: StateFlow<List<Appeal>> = _appeals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadAppeals(examId: String = "") {
        viewModelScope.launch {
            appealsRepository.getAppeals(examId = examId.takeIf { it.isNotBlank() }).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _appeals.value = result.data.data
                            .filter { appeal ->
                                val resolvedExam = appeal.resolvedExam()
                                examId.isBlank() || resolvedExam?.id == examId
                            }
                            .map { appeal -> appeal.toUiModel() }
                    }
                    is ApiResult.Error -> _isLoading.value = false
                }
            }
        }
    }

    private fun AppealSummaryResponse.toUiModel(): Appeal {
        val resolvedExam = resolvedExam()
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

    private fun AppealSummaryResponse.resolvedExam() = exam ?: sheet?.exam
}
