package com.examhub.student.service

import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.RefreshTokenRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.auth.AuthTokenResponse
import com.examhub.student.model.response.common.MessageResponse
import com.examhub.student.model.response.profile.MobileSessionResponse
import com.examhub.student.model.response.auth.OtpVerifyResponse
import com.examhub.student.model.response.auth.RefreshTokenResponse
import com.examhub.student.model.response.auth.StudentRegisterResponse
import com.examhub.student.model.response.profile.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import okhttp3.MultipartBody

interface AuthApiService {
    @POST("student/auth/register")
    suspend fun registerStudent(@Body request: StudentRegisterRequest): Response<StudentRegisterResponse>

    @POST("student/auth/otp/request")
    suspend fun requestStudentOtp(@Body request: OtpRequest): Response<MessageResponse>

    @POST("student/auth/otp/verify")
    suspend fun verifyStudentOtp(@Body request: OtpVerifyRequest): Response<OtpVerifyResponse>

    @POST("student/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthTokenResponse>

    @POST("student/auth/login")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<AuthTokenResponse>

    @POST("student/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("student/auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @POST("student/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<MessageResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<UserResponse>

    @PATCH("auth/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserResponse>

    @Multipart
    @POST("auth/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<UserResponse>

    @GET("auth/sessions")
    suspend fun getSessions(): Response<List<MobileSessionResponse>>

    @DELETE("auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<MessageResponse>
}
