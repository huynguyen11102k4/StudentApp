package com.examhub.student.ui.forgotpassword

import androidx.lifecycle.ViewModel
import com.examhub.student.R
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ForgotPasswordViewModel(
    @Suppress("unused") private val authRepository: AuthRepository,
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

    fun sendOTP(email: String) {
        if (email.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.login_validation_email_required))
            return
        }
        _errorMessage.tryEmit(resources.getString(R.string.forgot_password_student_unsupported))
    }

    fun verifyOTP(otp: String) {
        if (otp.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.forgot_password_validation_otp_required))
            return
        }
        _errorMessage.tryEmit(resources.getString(R.string.forgot_password_student_unsupported))
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_short))
            return
        }
        if (newPassword != confirmPassword) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_mismatch))
            return
        }
        _errorMessage.tryEmit(resources.getString(R.string.forgot_password_student_unsupported))
    }
}
