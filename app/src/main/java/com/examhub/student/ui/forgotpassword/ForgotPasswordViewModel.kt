package com.examhub.student.ui.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.ForgotPasswordRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.ResetPasswordRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
    private val resources: ResourceProvider
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
        if (_isLoading.value) return
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.login_validation_email_required))
            return
        }
        savedEmail = normalizedEmail

        viewModelScope.launch {
            authRepository.requestForgotPassword(ForgotPasswordRequest(normalizedEmail)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _currentStep.value = 2
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.forgot_password_otp_failed))
                    }
                }
            }
        }
    }

    fun verifyOTP(otp: String) {
        if (_isLoading.value) return
        val normalizedOtp = otp.filter(Char::isDigit)
        if (savedEmail.isBlank()) {
            _currentStep.value = 1
            _errorMessage.tryEmit(resources.getString(R.string.login_validation_email_required))
            return
        }
        if (normalizedOtp.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.forgot_password_validation_otp_required))
            return
        }
        viewModelScope.launch {
            authRepository.verifyStudentOtp(
                OtpVerifyRequest(
                    email = savedEmail,
                    otpCode = normalizedOtp,
                    purpose = "forgot_password"
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        if (result.data.verified) {
                            savedOtpCode = normalizedOtp
                            _currentStep.value = 3
                        } else {
                            _errorMessage.tryEmit(result.data.message.ifBlank {
                                resources.getString(R.string.forgot_password_validation_otp_required)
                            })
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.forgot_password_validation_otp_required))
                    }
                }
            }
        }
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (_isLoading.value) return
        if (savedEmail.isBlank()) {
            _currentStep.value = 1
            _errorMessage.tryEmit(resources.getString(R.string.login_validation_email_required))
            return
        }
        if (savedOtpCode.isBlank()) {
            _currentStep.value = 2
            _errorMessage.tryEmit(resources.getString(R.string.forgot_password_validation_otp_required))
            return
        }
        if (newPassword.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_short))
            return
        }
        if (newPassword != confirmPassword) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_mismatch))
            return
        }
        viewModelScope.launch {
            authRepository.resetPassword(
                ResetPasswordRequest(
                    email = savedEmail,
                    otpCode = savedOtpCode,
                    newPassword = newPassword
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _passwordResetSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.forgot_password_reset_failed))
                    }
                }
            }
        }
    }

    fun resetFlow() {
        _currentStep.value = 1
        _isLoading.value = false
        savedEmail = ""
        savedOtpCode = ""
    }
}
