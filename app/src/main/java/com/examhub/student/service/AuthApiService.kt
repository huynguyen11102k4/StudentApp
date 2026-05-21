package com.examhub.student.service

import com.examhub.student.model.request.ChangePasswordRequest
import com.examhub.student.model.request.GoogleLoginRequest
import com.examhub.student.model.request.LoginRequest
import com.examhub.student.model.request.OtpRequest
import com.examhub.student.model.request.OtpVerifyRequest
import com.examhub.student.model.request.RefreshTokenRequest
import com.examhub.student.model.request.StudentRegisterRequest
import com.examhub.student.model.request.UpdateProfileRequest
import com.examhub.student.model.response.AuthTokenResponse
import com.examhub.student.model.response.MessageResponse
import com.examhub.student.model.response.MobileSessionResponse
import com.examhub.student.model.response.OtpVerifyResponse
import com.examhub.student.model.response.RefreshTokenResponse
import com.examhub.student.model.response.StudentRegisterResponse
import com.examhub.student.model.response.UserResponse
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

    @GET("student/auth/me")
    suspend fun getMe(): Response<UserResponse>

    @PATCH("student/auth/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<UserResponse>

    @Multipart
    @POST("student/auth/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<UserResponse>

    @GET("auth/sessions")
    suspend fun getSessions(): Response<List<MobileSessionResponse>>

    @DELETE("auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<MessageResponse>
}
