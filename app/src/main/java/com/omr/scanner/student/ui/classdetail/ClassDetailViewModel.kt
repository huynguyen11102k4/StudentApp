package com.omr.scanner.student.ui.classdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.MobileClassResponse
import com.omr.scanner.student.repository.ClassRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClassDetailViewModel(
    private val classRepository: ClassRepository
) : ViewModel() {
    private val _classDetail = MutableStateFlow<MobileClassResponse?>(null)
    val classDetail: StateFlow<MobileClassResponse?> = _classDetail.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    fun load(classId: String) {
        if (classId.isBlank()) return
        viewModelScope.launch {
            classRepository.getClassDetail(classId).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _classDetail.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: "Không thể tải chi tiết lớp")
                    }
                }
            }
        }
    }
}
