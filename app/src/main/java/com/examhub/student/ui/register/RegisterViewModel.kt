package com.examhub.student.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
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
            _errorMessage.tryEmit("Vui lòng nhập họ tên")
            return
        }
        if (trimmedEmail.isBlank()) {
            _errorMessage.tryEmit("Vui lòng nhập email")
            return
        }
        if (!isGoogleRegistration && password.length < 6) {
            _errorMessage.tryEmit("Mật khẩu phải có ít nhất 6 ký tự")
            return
        }
        if (!isGoogleRegistration && password != confirmPassword) {
            _errorMessage.tryEmit("Mật khẩu không khớp")
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
                            _message.tryEmit("Đăng ký thành công")
                            _registerSuccess.tryEmit(RegisterDestination.Dashboard)
                        } else if (isGoogleRegistration) {
                            _message.tryEmit(result.data.message ?: "Đăng ký thành công. Vui lòng đăng nhập.")
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        } else if (result.data.requiresOtp) {
                            _currentStep.value = 2
                            _message.tryEmit(result.data.message ?: "Mã OTP đã được gửi đến email đăng ký")
                        } else {
                            _message.tryEmit(result.data.message ?: "Đăng ký thành công. Vui lòng đăng nhập.")
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
                            _errorMessage.tryEmit(result.exception.message ?: "Đăng ký thất bại")
                        }
                    }
                }
            }
        }
    }

    fun resendOtp() {
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit("Email đăng ký không hợp lệ")
            return
        }
        viewModelScope.launch {
            authRepository.requestStudentOtp(OtpRequest(email = registeredEmail)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _message.tryEmit(result.data.message.ifBlank { "Mã OTP đã được gửi lại" })
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (result.exception.isOtpRateLimited()) {
                            _message.tryEmit("OTP đã được gửi trước đó. Hãy kiểm tra email hoặc thử lại sau ít phút.")
                        } else {
                            _errorMessage.tryEmit(result.exception.message ?: "Gửi OTP thất bại")
                        }
                    }
                }
            }
        }
    }

    fun verifyOtp(otp: String) {
        if (otp.length < 6) {
            _errorMessage.tryEmit("Vui lòng nhập đầy đủ mã OTP")
            return
        }
        if (registeredEmail.isBlank()) {
            _errorMessage.tryEmit("Email đăng ký không hợp lệ")
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
                            _message.tryEmit(result.data.message.ifBlank { "Xác thực thành công. Vui lòng đăng nhập." })
                            _registerSuccess.tryEmit(RegisterDestination.Login)
                        } else {
                            _errorMessage.tryEmit(result.data.message.ifBlank { "OTP không hợp lệ" })
                        }
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Xác thực OTP thất bại")
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
