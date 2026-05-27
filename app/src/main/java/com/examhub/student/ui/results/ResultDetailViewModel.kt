package com.examhub.student.ui.results

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.appeal.StudentAppealItemRequest
import com.examhub.student.model.request.appeal.StudentAppealRequest
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.repository.AppealsRepository
import com.examhub.student.repository.ResultsRepository
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
    private val _appealCount = MutableStateFlow(0)
    val appealCount: StateFlow<Int> = _appealCount.asStateFlow()
    private val _isResultPending = MutableStateFlow(false)
    val isResultPending: StateFlow<Boolean> = _isResultPending.asStateFlow()

    fun loadResult(sheetId: String) {
        viewModelScope.launch {
            resultsRepository.getResultDetail(sheetId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _result.value = result.data
                        _isResultPending.value = result.data.isPending()
                        if (!result.data.isPending()) {
                            loadAppealCount(result.data)
                        }
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
        val result = _result.value
        val sheetId = result?.id.orEmpty()
        if (sheetId.isBlank()) {
            _message.tryEmit(context.getString(R.string.result_detail_missing_sheet))
            return
        }
        if (result?.exam?.status.equals("CLOSED", ignoreCase = true)) {
            _message.tryEmit(context.getString(R.string.result_detail_appeal_closed_exam))
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
                        _appealCount.value = (_appealCount.value + 1).coerceAtLeast(1)
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

    private fun loadAppealCount(result: StudentResultDetailResponse) {
        viewModelScope.launch {
            appealsRepository.getAppeals(examId = result.exam?.id).collect { response ->
                if (response is ApiResult.Success) {
                    _appealCount.value = response.data.data.size
                }
            }
        }
    }

    private fun StudentResultDetailResponse.isPending(): Boolean {
        return resultStatus.equals("PENDING", ignoreCase = true) && id.isNullOrBlank()
    }
}
