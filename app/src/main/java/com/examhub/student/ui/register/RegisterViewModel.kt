package com.examhub.student.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val resources: ResourceProvider
) : ViewModel() {
    private val _registerSuccess = MutableSharedFlow<RegisterDestination>(extraBufferCapacity = 1)
    val registerSuccess: SharedFlow<RegisterDestination> = _registerSuccess.asSharedFlow()

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var registeredEmail: String = ""
    private var prefillInitialized = false

    fun initializePrefill(email: String, startActivation: Boolean) {
        if (prefillInitialized) return
        prefillInitialized = true

        registeredEmail = email.trim()
        if (startActivation && registeredEmail.isNotBlank()) {
            _currentStep.value = 2
            resendOtp()
        }
    }

    fun register(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        studentCode: String,
        googleIdToken: String,
        dateOfBirth: String? = null
    ) {
        val trimmedEmail = email.trim()
        val trimmedStudentCode = studentCode.trim()
        val isGoogleRegistration = googleIdToken.isNotBlank()

        if (fullName.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_name_required))
            return
        }
        if (trimmedEmail.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_email_required))
            return
        }
        if (!isGoogleRegistration && password.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_short))
            return
        }
        if (!isGoogleRegistration && password != confirmPassword) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_password_mismatch))
            return
        }
        if (trimmedStudentCode.isNotBlank() && !trimmedStudentCode.all(Char::isDigit)) {
            _errorMessage.tryEmit(resources.getString(R.string.register_validation_student_code_digits))
            return
        }

        viewModelScope.launch {
            authRepository.registerStudent(
                StudentRegisterRequest(
                    email = trimmedEmail,
                    fullName = fullName.trim(),
                    password = password.takeIf { !isGoogleRegistration },
                    googleIdToken = googleIdToken.takeIf { isGoogleRegistration },
                    studentCode = trimmedStudentCode.takeIf { it.isNotBlank() },
                    dateOfBirth = dateOfBirth?.trim()?.takeIf { it.isNotBlank() }
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        registeredEmail = trimmedEmail
                        if (
                            isGoogleRegistration &&
                            !result.data.accessToken.isNullOrBlank() &&
                            !result.data.refreshToken.isNullOrBlank()
                        ) {
                            fcmTokenRegistrar.syncCurrentToken(viewModelScope)
                            _message.tryEmit(resources.getString(R.string.register_success))
                            _registerSuccess.tryEmit(RegisterDestination.Dashboard)
                        } else if (isGoogleRegistration) {
                            _message.tryEmit(result.data.message ?: resources.getString(R.string.register_success_login))
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        } else if (result.data.requiresOtp) {
                            _currentStep.value = 2
                            _message.tryEmit(result.data.message ?: resources.getString(R.string.register_otp_sent))
                        } else {
                            _message.tryEmit(result.data.message ?: resources.getString(R.string.register_success_login))
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        registeredEmail = trimmedEmail
                        if (!isGoogleRegistration && result.exception.canContinueWithOtp()) {
                            _currentStep.value = 2
                            resendOtp()
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.register_failed))
                        }
                    }
                }
            }
        }
    }

    fun resendOtp() {
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.register_email_invalid))
            return
        }
        viewModelScope.launch {
            authRepository.requestStudentOtp(OtpRequest(email = registeredEmail)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _message.tryEmit(result.data.message.ifBlank { resources.getString(R.string.register_otp_resent) })
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (result.exception.isOtpRateLimited()) {
                            _message.tryEmit(resources.getString(R.string.register_otp_rate_limited))
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.register_otp_resend_failed))
                        }
                    }
                }
            }
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.length < 6) {
            _errorMessage.tryEmit(resources.getString(R.string.forgot_password_validation_otp_required))
            return
        }
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.register_email_invalid))
            return
        }

        viewModelScope.launch {
            authRepository.verifyStudentOtp(
                OtpVerifyRequest(
                    email = registeredEmail,
                    otpCode = otp
                )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        if (result.data.verified) {
                            _message.tryEmit(result.data.message.ifBlank { resources.getString(R.string.register_otp_success_login) })
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        } else {
                            _errorMessage.tryEmit(result.data.message.ifBlank { resources.getString(R.string.register_otp_invalid) })
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.register_otp_verify_failed))
                    }
                }
            }
        }
    }

    private fun ApiException.isOtpRateLimited(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        val searchableMessage = message.toSearchableText()
        return httpCode == 429 ||
            normalizedCode.contains("rate") ||
            normalizedCode.contains("too_many") ||
            normalizedMessage.contains("too many") ||
            normalizedMessage.contains("rate") ||
            searchableMessage.contains("qua nhieu")
    }

    private fun ApiException.canContinueWithOtp(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        val searchableMessage = message.toSearchableText()
        return httpCode == 409 ||
            normalizedCode.contains("already_exists") ||
            normalizedCode.contains("email_exists") ||
            normalizedCode.contains("account_inactive") ||
            normalizedCode.contains("inactive") ||
            normalizedMessage.contains("already exists") ||
            searchableMessage.contains("da ton tai") ||
            normalizedMessage.contains("inactive") ||
            searchableMessage.contains("chua kich hoat")
    }

    private fun String.toSearchableText(): String {
        return Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
    }
}

enum class RegisterDestination {
    Login,
    Dashboard
}
