package com.examhub.student.ui.forgotpassword

import androidx.lifecycle.ViewModel
import com.examhub.student.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ForgotPasswordViewModel(
    @Suppress("unused") private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _passwordResetSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val passwordResetSuccess: SharedFlow<Unit> = _passwordResetSuccess.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun sendOTP(email: String) {
        if (email.isBlank()) {
            _errorMessage.tryEmit("Vui long nhap email")
            return
        }
        _errorMessage.tryEmit(UNSUPPORTED_FOR_STUDENT)
    }

    fun verifyOTP(otp: String) {
        if (otp.length < 6) {
            _errorMessage.tryEmit("Vui long nhap day du ma OTP")
            return
        }
        _errorMessage.tryEmit(UNSUPPORTED_FOR_STUDENT)
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 6) {
            _errorMessage.tryEmit("Mat khau phai co it nhat 6 ky tu")
            return
        }
        if (newPassword != confirmPassword) {
            _errorMessage.tryEmit("Mat khau khong khop")
            return
        }
        _errorMessage.tryEmit(UNSUPPORTED_FOR_STUDENT)
    }

    private companion object {
        const val UNSUPPORTED_FOR_STUDENT = "Chuc nang quen mat khau hoc sinh chua duoc backend ho tro"
    }
}
