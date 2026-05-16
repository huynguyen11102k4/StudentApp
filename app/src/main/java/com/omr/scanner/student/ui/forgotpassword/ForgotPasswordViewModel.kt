package com.omr.scanner.student.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.ForgotPasswordRequest
import com.omr.scanner.student.model.request.ResetPasswordRequest
import com.omr.scanner.student.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _passwordResetSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val passwordResetSuccess: SharedFlow<Unit> = _passwordResetSuccess.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var savedEmail: String = ""
    private var savedOtpCode: String = ""

    fun sendOTP(email: String) {
        if (email.isBlank()) { _errorMessage.tryEmit("Vui lòng nhập email"); return }
        savedEmail = email

        viewModelScope.launch {
            _isLoading.value = true
            authRepository.requestForgotPassword(ForgotPasswordRequest(email)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {}
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _currentStep.value = 2
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Gửi OTP thất bại")
                    }
                }
            }
        }
    }

    fun verifyOTP(otp: String) {
        if (otp.length < 6) { _errorMessage.tryEmit("Vui lòng nhập đầy đủ mã OTP"); return }
        savedOtpCode = otp
        _currentStep.value = 3
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 6) { _errorMessage.tryEmit("Mật khẩu phải có ít nhất 6 ký tự"); return }
        if (newPassword != confirmPassword) { _errorMessage.tryEmit("Mật khẩu không khớp"); return }

        viewModelScope.launch {
            _isLoading.value = true
            authRepository.resetPassword(
                ResetPasswordRequest(
                    email = savedEmail,
                    otpCode = savedOtpCode,
                    newPassword = newPassword
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> {}
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _passwordResetSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Đặt lại mật khẩu thất bại")
                    }
                }
            }
        }
    }
}
