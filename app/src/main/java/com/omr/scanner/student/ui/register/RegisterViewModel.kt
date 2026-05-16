package com.omr.scanner.student.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.StudentRegisterRequest
import com.omr.scanner.student.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _registerSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val registerSuccess: SharedFlow<Unit> = _registerSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun register(fullName: String, email: String, password: String, confirmPassword: String) {
        if (fullName.isBlank()) { _errorMessage.tryEmit("Vui lòng nhập họ tên"); return }
        if (email.isBlank()) { _errorMessage.tryEmit("Vui lòng nhập email"); return }
        if (password.length < 6) { _errorMessage.tryEmit("Mật khẩu phải có ít nhất 6 ký tự"); return }
        if (password != confirmPassword) { _errorMessage.tryEmit("Mật khẩu không khớp"); return }

        viewModelScope.launch {
            authRepository.registerStudent(
                StudentRegisterRequest(
                    email = email,
                    fullName = fullName,
                    password = password
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _registerSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Đăng ký thất bại")
                    }
                }
            }
        }
    }
}
