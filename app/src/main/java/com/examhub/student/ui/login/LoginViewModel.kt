package com.examhub.student.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.GoogleLoginRequest
import com.examhub.student.model.request.LoginRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar
) : ViewModel() {

    private val _loginSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<Unit> = _loginSuccess.asSharedFlow()

    private val _googleRegistrationRequired = MutableSharedFlow<GoogleRegisterPrefill>(extraBufferCapacity = 1)
    val googleRegistrationRequired: SharedFlow<GoogleRegisterPrefill> = _googleRegistrationRequired.asSharedFlow()

    private val _activationRequired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val activationRequired: SharedFlow<String> = _activationRequired.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _errorMessage.tryEmit("Vui lòng nhập email")
            return
        }
        if (password.isBlank()) {
            _errorMessage.tryEmit("Vui lòng nhập mật khẩu")
            return
        }

        viewModelScope.launch {
            authRepository.login(LoginRequest(email, password)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        fcmTokenRegistrar.syncCurrentToken(viewModelScope)
                        _loginSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (result.exception.isAccountInactive()) {
                            _activationRequired.tryEmit(email.trim())
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: "Đăng nhập thất bại")
                        }
                    }
                }
            }
        }
    }

    fun loginWithGoogle(idToken: String, email: String? = null, fullName: String? = null) {
        if (idToken.isBlank()) {
            _errorMessage.tryEmit("Không thể lấy token từ Google")
            return
        }

        viewModelScope.launch {
            authRepository.loginWithGoogle(GoogleLoginRequest(email = email, googleIdToken = idToken)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        fcmTokenRegistrar.syncCurrentToken(viewModelScope)
                        _loginSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (result.exception.requiresRegistrationOrActivation()) {
                            _googleRegistrationRequired.tryEmit(
                                GoogleRegisterPrefill(
                                    email = email.orEmpty(),
                                    fullName = fullName.orEmpty(),
                                    googleIdToken = idToken,
                                    startActivation = result.exception.isAccountInactive()
                                )
                            )
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: "Đăng nhập Google thất bại")
                        }
                    }
                }
            }
        }
    }

    private fun ApiException.requiresRegistrationOrActivation(): Boolean {
        return isUserNotRegistered() || isAccountInactive()
    }

    private fun ApiException.isUserNotRegistered(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        return httpCode == 404 ||
            normalizedCode.contains("account_not_found") ||
            normalizedCode.contains("user_not_found") ||
            normalizedCode.contains("student_not_found") ||
            normalizedCode.contains("email_not_registered") ||
            normalizedCode.contains("not_found") ||
            normalizedCode.contains("not_registered") ||
            normalizedCode.contains("user_not_registered") ||
            normalizedMessage.contains("account not found") ||
            normalizedMessage.contains("user not found") ||
            normalizedMessage.contains("student not found") ||
            normalizedMessage.contains("email not registered") ||
            normalizedMessage.contains("not registered") ||
            normalizedMessage.contains("not found") ||
            normalizedMessage.contains("chưa đăng ký") ||
            normalizedMessage.contains("chua dang ky")
    }

    private fun ApiException.isAccountInactive(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        return normalizedCode.contains("account_inactive") ||
            normalizedCode.contains("inactive") ||
            normalizedMessage.contains("inactive") ||
            normalizedMessage.contains("chua kich hoat") ||
            normalizedMessage.contains("chÆ°a kÃ­ch hoáº¡t") ||
            normalizedMessage.contains("xac thuc otp") ||
            normalizedMessage.contains("xÃ¡c thá»±c otp")
    }
}

data class GoogleRegisterPrefill(
    val email: String,
    val fullName: String,
    val googleIdToken: String,
    val startActivation: Boolean = false
)
