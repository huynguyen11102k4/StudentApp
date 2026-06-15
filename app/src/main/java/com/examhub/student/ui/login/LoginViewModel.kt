package com.examhub.student.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

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

    // Emits error message codes (e.g. "email_blank") or raw API error messages
    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            _errorMessage.tryEmit("email_blank")
            return
        }
        if (password.isBlank()) {
            _errorMessage.tryEmit("password_blank")
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
                            _errorMessage.tryEmit(result.exception.displayCode("login_failed"))
                        }
                    }
                }
            }
        }
    }

    fun loginWithGoogle(idToken: String, email: String? = null, fullName: String? = null) {
        if (idToken.isBlank()) {
            _errorMessage.tryEmit("google_token_missing")
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
                        val normalizedEmail = email.orEmpty().trim()
                        if (result.exception.isAccountInactive() && normalizedEmail.isNotBlank()) {
                            _activationRequired.tryEmit(normalizedEmail)
                        } else if (result.exception.requiresGoogleRegistration()) {
                            _googleRegistrationRequired.tryEmit(
                                GoogleRegisterPrefill(
                                    email = normalizedEmail,
                                    fullName = fullName.orEmpty(),
                                    googleIdToken = idToken,
                                    startActivation = false
                                )
                            )
                        } else {
                            _errorMessage.tryEmit(result.exception.displayCode("google_login_failed"))
                        }
                    }
                }
            }
        }
    }

    private fun ApiException.isUserNotRegistered(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        val searchableMessage = message.toSearchableText()
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
            normalizedMessage.contains("not exist") ||
            normalizedMessage.contains("does not exist") ||
            searchableMessage.contains("chua dang ky")
    }

    private fun ApiException.requiresGoogleRegistration(): Boolean {
        if (isUserNotRegistered()) return true
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        val searchableMessage = message.toSearchableText()
        return normalizedCode.contains("registration_required") ||
                normalizedCode.contains("register_required") ||
                normalizedCode.contains("account_required") ||
                normalizedCode.contains("not_linked") ||
                normalizedMessage.contains("not linked") ||
                searchableMessage.contains("chua lien ket") ||
                searchableMessage.contains("can dang ky") ||
                searchableMessage.contains("vui long dang ky")
    }

    private fun ApiException.isAccountInactive(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        val searchableMessage = message.toSearchableText()
        return normalizedCode.contains("account_inactive") ||
            normalizedCode.contains("inactive") ||
            normalizedMessage.contains("inactive") ||
            searchableMessage.contains("chua kich hoat") ||
            searchableMessage.contains("xac thuc otp")
    }

    private fun ApiException.displayCode(fallback: String): String {
        return code.uppercase().takeIf { it in AUTH_ERROR_CODES }
            ?: message.takeIf(String::isNotBlank)
            ?: fallback
    }

    private fun String.toSearchableText(): String {
        return Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
    }

    private companion object {
        val AUTH_ERROR_CODES = setOf(
            "INVALID_GOOGLE_TOKEN",
            "GOOGLE_EMAIL_MISMATCH",
            "GOOGLE_ACCOUNT_ALREADY_LINKED",
            "GOOGLE_ACCOUNT_MISMATCH",
            "ACCOUNT_INACTIVE",
            "INVALID_CREDENTIALS"
        )
    }
}

data class GoogleRegisterPrefill(
    val email: String,
    val fullName: String,
    val googleIdToken: String,
    val startActivation: Boolean = false
)
