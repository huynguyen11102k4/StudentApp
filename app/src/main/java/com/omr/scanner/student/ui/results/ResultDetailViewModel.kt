package com.omr.scanner.student.ui.results

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.R
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.StudentAppealItemRequest
import com.omr.scanner.student.model.request.StudentAppealRequest
import com.omr.scanner.student.model.response.StudentResultDetailResponse
import com.omr.scanner.student.repository.AppealsRepository
import com.omr.scanner.student.repository.ResultsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultDetailViewModel(
    private val resultsRepository: ResultsRepository,
    private val appealsRepository: AppealsRepository,
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

    fun loadResult(sheetId: String) {
        viewModelScope.launch {
            resultsRepository.getResultDetail(sheetId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _result.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.result_detail_load_failed))
                    }
                }
            }
        }
    }

    fun createAppeal(reason: String, questionNumber: Int?, questionMessage: String) {
        val sheetId = _result.value?.id.orEmpty()
        if (sheetId.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_missing_sheet))
            return
        }
        val normalizedReason = reason.trim()
        if (normalizedReason.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_reason_required))
            return
        }
        val items = questionNumber?.takeIf { it > 0 }?.let {
            listOf(StudentAppealItemRequest(it, questionMessage.ifBlank { normalizedReason }))
        }.orEmpty()

        viewModelScope.launch {
            appealsRepository.createAppeal(StudentAppealRequest(sheetId, normalizedReason, items)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
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
}
