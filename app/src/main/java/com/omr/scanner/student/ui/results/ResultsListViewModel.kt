package com.omr.scanner.student.ui.results

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.R
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.StudentResultSummaryResponse
import com.omr.scanner.student.repository.ResultsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResultsListViewModel(
    private val resultsRepository: ResultsRepository,
    private val context: Context
) : ViewModel() {
    private val _results = MutableStateFlow<List<StudentResultSummaryResponse>>(emptyList())
    val results: StateFlow<List<StudentResultSummaryResponse>> = _results.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun loadResults() {
        viewModelScope.launch {
            resultsRepository.getResults().collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _results.value = result.data.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: context.getString(R.string.results_load_failed))
                    }
                }
            }
        }
    }
}
