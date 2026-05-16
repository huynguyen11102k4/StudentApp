package com.omr.scanner.student.service

import com.omr.scanner.student.model.request.ChangePasswordRequest
import com.omr.scanner.student.model.request.ForgotPasswordRequest
import com.omr.scanner.student.model.request.GoogleLoginRequest
import com.omr.scanner.student.model.request.LoginRequest
import com.omr.scanner.student.model.request.OtpRequest
import com.omr.scanner.student.model.request.OtpVerifyRequest
import com.omr.scanner.student.model.request.RefreshTokenRequest
import com.omr.scanner.student.model.request.ResetPasswordRequest
import com.omr.scanner.student.model.request.StudentRegisterRequest
import com.omr.scanner.student.model.request.UpdateProfileRequest
import com.omr.scanner.student.model.response.AuthTokenResponse
import com.omr.scanner.student.model.response.MessageResponse
import com.omr.scanner.student.model.response.MobileSessionResponse
import com.omr.scanner.student.model.response.OtpVerifyResponse
import com.omr.scanner.student.model.response.RefreshTokenResponse
import com.omr.scanner.student.model.response.UserResponse
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
    suspend fun registerStudent(@Body request: StudentRegisterRequest): Response<AuthTokenResponse>

    @POST("student/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthTokenResponse>

    @POST("student/auth/login")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<AuthTokenResponse>

    @POST("student/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("student/auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @POST("auth/logout-all")
    suspend fun logoutAll(): Response<MessageResponse>

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body request: OtpRequest): Response<MessageResponse>

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<OtpVerifyResponse>

    @POST("auth/forgot-password/request")
    suspend fun requestForgotPassword(@Body request: ForgotPasswordRequest): Response<MessageResponse>

    @POST("auth/forgot-password/confirm")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<MessageResponse>

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
