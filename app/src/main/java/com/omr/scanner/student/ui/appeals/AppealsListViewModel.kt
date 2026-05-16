package com.omr.scanner.student.ui.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.data.model.Appeal
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.repository.AppealsRepository
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
                            .filter { appeal -> examId.isBlank() || appeal.exam?.id == examId }
                            .map { appeal ->
                            Appeal(
                                id = appeal.id,
                                studentId = appeal.student?.id ?: "",
                                studentName = appeal.student?.name ?: "",
                                studentCode = appeal.student?.code ?: "",
                                examId = appeal.exam?.id ?: "",
                                examName = appeal.exam?.name ?: "",
                                subject = appeal.exam?.subject ?: "",
                                sheetId = appeal.sheet?.id ?: "",
                                oldScore = appeal.oldScore ?: 0.0,
                                newScore = appeal.newScore,
                                reason = appeal.reason ?: "",
                                status = appeal.status,
                                createdAt = appeal.createdAt,
                                teacherNote = appeal.teacherNote,
                                processedImageUrl = appeal.sheet?.processedImageUrl,
                                dewarpedImageUrl = appeal.sheet?.dewarpedImageUrl,
                                itemMessages = appeal.items.orEmpty().joinToString("\n") { item ->
                                    "Câu ${item.questionNumber}: ${item.studentMessage.orEmpty().ifBlank { item.status }}"
                                }
                            )
                        }
                    }
                    is ApiResult.Error -> _isLoading.value = false
                }
            }
        }
    }
}
