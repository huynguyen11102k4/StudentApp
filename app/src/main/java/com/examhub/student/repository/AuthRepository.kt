package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.auth.AuthTokenResponse
import com.examhub.student.model.response.common.MessageResponse
import com.examhub.student.model.response.profile.MobileSessionResponse
import com.examhub.student.model.response.auth.OtpVerifyResponse
import com.examhub.student.model.response.auth.StudentRegisterResponse
import com.examhub.student.model.response.profile.UserResponse
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
