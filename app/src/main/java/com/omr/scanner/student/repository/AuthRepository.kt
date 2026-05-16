package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.ChangePasswordRequest
import com.omr.scanner.student.model.request.ForgotPasswordRequest
import com.omr.scanner.student.model.request.GoogleLoginRequest
import com.omr.scanner.student.model.request.LoginRequest
import com.omr.scanner.student.model.request.OtpRequest
import com.omr.scanner.student.model.request.OtpVerifyRequest
import com.omr.scanner.student.model.request.ResetPasswordRequest
import com.omr.scanner.student.model.request.StudentRegisterRequest
import com.omr.scanner.student.model.request.UpdateProfileRequest
import com.omr.scanner.student.model.response.AuthTokenResponse
import com.omr.scanner.student.model.response.MessageResponse
import com.omr.scanner.student.model.response.MobileSessionResponse
import com.omr.scanner.student.model.response.OtpVerifyResponse
import com.omr.scanner.student.model.response.UserResponse
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

interface AuthRepository {
    fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<AuthTokenResponse>>
    fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>>
    fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>>
    fun logout(): Flow<ApiResult<MessageResponse>>
    fun logoutAll(): Flow<ApiResult<MessageResponse>>
    fun requestOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>>
    fun verifyOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>>
    fun requestForgotPassword(request: ForgotPasswordRequest): Flow<ApiResult<MessageResponse>>
    fun resetPassword(request: ResetPasswordRequest): Flow<ApiResult<MessageResponse>>
    fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>>
    fun getMe(): Flow<ApiResult<UserResponse>>
    fun updateProfile(request: UpdateProfileRequest): Flow<ApiResult<UserResponse>>
    fun uploadAvatar(file: MultipartBody.Part): Flow<ApiResult<UserResponse>>
    fun getSessions(): Flow<ApiResult<List<MobileSessionResponse>>>
    fun revokeSession(sessionId: String): Flow<ApiResult<MessageResponse>>
}
