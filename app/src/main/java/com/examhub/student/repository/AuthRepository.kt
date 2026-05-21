package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.ChangePasswordRequest
import com.examhub.student.model.request.GoogleLoginRequest
import com.examhub.student.model.request.LoginRequest
import com.examhub.student.model.request.OtpRequest
import com.examhub.student.model.request.OtpVerifyRequest
import com.examhub.student.model.request.StudentRegisterRequest
import com.examhub.student.model.request.UpdateProfileRequest
import com.examhub.student.model.response.AuthTokenResponse
import com.examhub.student.model.response.MessageResponse
import com.examhub.student.model.response.MobileSessionResponse
import com.examhub.student.model.response.OtpVerifyResponse
import com.examhub.student.model.response.StudentRegisterResponse
import com.examhub.student.model.response.UserResponse
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody

interface AuthRepository {
    fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<StudentRegisterResponse>>
    fun requestStudentOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>>
    fun verifyStudentOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>>
    fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>>
    fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>>
    fun logout(): Flow<ApiResult<MessageResponse>>
    fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>>
    fun getMe(): Flow<ApiResult<UserResponse>>
    fun updateProfile(request: UpdateProfileRequest): Flow<ApiResult<UserResponse>>
    fun uploadAvatar(file: MultipartBody.Part): Flow<ApiResult<UserResponse>>
    fun getSessions(): Flow<ApiResult<List<MobileSessionResponse>>>
    fun revokeSession(sessionId: String): Flow<ApiResult<MessageResponse>>
}
