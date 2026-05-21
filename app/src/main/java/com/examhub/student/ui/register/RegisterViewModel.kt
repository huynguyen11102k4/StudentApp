package com.examhub.student.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.OtpRequest
import com.examhub.student.model.request.OtpVerifyRequest
import com.examhub.student.model.request.StudentRegisterRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar
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
        googleIdToken: String
    ) {
        val trimmedEmail = email.trim()
        val trimmedStudentCode = studentCode.trim()
        val isGoogleRegistration = googleIdToken.isNotBlank()

        if (fullName.isBlank()) {
            _errorMessage.tryEmit("Vui long nhap ho ten")
            return
        }
        if (trimmedEmail.isBlank()) {
            _errorMessage.tryEmit("Vui long nhap email")
            return
        }
        if (!isGoogleRegistration && password.length < 6) {
            _errorMessage.tryEmit("Mat khau phai co it nhat 6 ky tu")
            return
        }
        if (!isGoogleRegistration && password != confirmPassword) {
            _errorMessage.tryEmit("Mat khau khong khop")
            return
        }

        viewModelScope.launch {
            authRepository.registerStudent(
                StudentRegisterRequest(
                    email = trimmedEmail,
                    fullName = fullName.trim(),
                    password = password.takeIf { !isGoogleRegistration },
                    googleIdToken = googleIdToken.takeIf { isGoogleRegistration },
                    studentCode = trimmedStudentCode.takeIf { it.isNotBlank() }
                    )
            ).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        registeredEmail = trimmedEmail
                        if (isGoogleRegistration && !result.data.accessToken.isNullOrBlank()) {
                            fcmTokenRegistrar.syncCurrentToken(viewModelScope)
                            _message.tryEmit("Dang ky thanh cong")
                            _registerSuccess.tryEmit(RegisterDestination.Dashboard)
                        } else if (result.data.requiresOtp) {
                            _currentStep.value = 2
                            _message.tryEmit(result.data.message ?: "Ma OTP da duoc gui den email dang ky")
                        } else {
                            _message.tryEmit(result.data.message ?: "Dang ky thanh cong. Vui long dang nhap.")
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
                            _errorMessage.tryEmit(result.exception.message ?: "Dang ky that bai")
                        }
                    }
                }
            }
        }
    }

    fun resendOtp() {
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit("Email dang ky khong hop le")
            return
        }
        viewModelScope.launch {
            authRepository.requestStudentOtp(OtpRequest(email = registeredEmail)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _message.tryEmit(result.data.message.ifBlank { "Ma OTP da duoc gui lai" })
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (result.exception.isOtpRateLimited()) {
                            _message.tryEmit("OTP da duoc gui truoc do. Hay kiem tra email hoac thu lai sau it phut.")
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: "Gui OTP that bai")
                        }
                    }
                }
            }
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.length < 6) {
            _errorMessage.tryEmit("Vui long nhap day du ma OTP")
            return
        }
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit("Email dang ky khong hop le")
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
                            _message.tryEmit(result.data.message.ifBlank { "Xac thuc thanh cong. Vui long dang nhap." })
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        } else {
                            _errorMessage.tryEmit(result.data.message.ifBlank { "OTP khong hop le" })
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Xac thuc OTP that bai")
                    }
                }
            }
        }
    }

    private fun ApiException.isOtpRateLimited(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        return httpCode == 429 ||
            normalizedCode.contains("rate") ||
            normalizedCode.contains("too_many") ||
            normalizedMessage.contains("too many") ||
            normalizedMessage.contains("rate") ||
            normalizedMessage.contains("qua nhieu") ||
            normalizedMessage.contains("quÃ¡ nhiá»u")
    }

    private fun ApiException.canContinueWithOtp(): Boolean {
        val normalizedCode = code.lowercase()
        val normalizedMessage = message.lowercase()
        return httpCode == 409 ||
            normalizedCode.contains("already_exists") ||
            normalizedCode.contains("email_exists") ||
            normalizedCode.contains("account_inactive") ||
            normalizedCode.contains("inactive") ||
            normalizedMessage.contains("already exists") ||
            normalizedMessage.contains("da ton tai") ||
            normalizedMessage.contains("Ä‘Ã£ tá»“n táº¡i") ||
            normalizedMessage.contains("inactive") ||
            normalizedMessage.contains("chua kich hoat") ||
            normalizedMessage.contains("chÆ°a kÃ­ch hoáº¡t")
    }
}

enum class RegisterDestination {
    Login,
    Dashboard
}
