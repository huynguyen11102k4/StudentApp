package com.examhub.student.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.util.helper.AuthErrorMapper
import com.examhub.student.util.helper.GoogleLoginFailure
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val resources: ResourceProvider
) : ViewModel() {
    private val _loginSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val loginSuccess: SharedFlow<Unit> = _loginSuccess.asSharedFlow()

    private val _googleRegistrationRequired =
        MutableSharedFlow<GoogleRegisterPrefill>(extraBufferCapacity = 1)
    val googleRegistrationRequired: SharedFlow<GoogleRegisterPrefill> =
        _googleRegistrationRequired.asSharedFlow()

    private val _activationRequired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val activationRequired: SharedFlow<String> = _activationRequired.asSharedFlow()

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
                    ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> completeLogin()
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (AuthErrorMapper.googleLoginFailure(result.exception) ==
                            GoogleLoginFailure.ACTIVATE_ACCOUNT
                        ) {
                            _activationRequired.tryEmit(email.trim())
                        } else {
                            _errorMessage.tryEmit(
                                AuthErrorMapper.message(
                                    result.exception,
                                    resources,
                                    R.string.login_error_failed
                                )
                            )
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
            authRepository.loginWithGoogle(
                GoogleLoginRequest(email = email, googleIdToken = idToken)
            ).collect { result ->
                when (result) {
                    ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> completeLogin()
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        val normalizedEmail = email.orEmpty().trim()
                        when (AuthErrorMapper.googleLoginFailure(result.exception)) {
                            GoogleLoginFailure.ACTIVATE_ACCOUNT -> {
                                if (normalizedEmail.isNotBlank()) {
                                    _activationRequired.tryEmit(normalizedEmail)
                                } else {
                                    _errorMessage.tryEmit(
                                        resources.getString(R.string.auth_error_account_inactive)
                                    )
                                }
                            }
                            GoogleLoginFailure.REGISTER_ACCOUNT -> {
                                _googleRegistrationRequired.tryEmit(
                                    GoogleRegisterPrefill(
                                        email = normalizedEmail,
                                        fullName = fullName.orEmpty(),
                                        googleIdToken = idToken
                                    )
                                )
                            }
                            GoogleLoginFailure.ACCOUNT_NOT_LINKED -> {
                                _errorMessage.tryEmit(
                                    resources.getString(R.string.auth_error_google_not_linked)
                                )
                            }
                            GoogleLoginFailure.SHOW_ERROR -> {
                                _errorMessage.tryEmit(
                                    AuthErrorMapper.message(
                                        result.exception,
                                        resources,
                                        R.string.login_error_google_failed
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun completeLogin() {
        _isLoading.value = false
        fcmTokenRegistrar.syncCurrentToken(viewModelScope)
        _loginSuccess.tryEmit(Unit)
    }
}

data class GoogleRegisterPrefill(
    val email: String,
    val fullName: String,
    val googleIdToken: String,
    val startActivation: Boolean = false
)
