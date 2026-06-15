package com.examhub.student.service

import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.request.auth.ForgotPasswordRequest
import com.examhub.student.model.request.auth.GoogleLinkRequest
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.RefreshTokenRequest
import com.examhub.student.model.request.auth.ResetPasswordRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.common.MessageResponse
import com.examhub.student.model.response.profile.MobileSessionResponse
import com.examhub.student.model.response.auth.OtpVerifyResponse
import com.examhub.student.model.response.auth.RefreshTokenResponse
import com.google.gson.JsonElement
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
    suspend fun registerStudent(@Body request: StudentRegisterRequest): Response<JsonElement>

    @POST("student/auth/otp/request")
    suspend fun requestStudentOtp(@Body request: OtpRequest): Response<MessageResponse>

    @POST("student/auth/otp/verify")
    suspend fun verifyStudentOtp(@Body request: OtpVerifyRequest): Response<OtpVerifyResponse>

    @POST("student/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<JsonElement>

    @POST("student/auth/login")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): Response<JsonElement>

    @POST("student/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("student/auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @POST("student/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<MessageResponse>

    @POST("student/auth/forgot-password/request")
    suspend fun requestForgotPassword(@Body request: ForgotPasswordRequest): Response<JsonElement>

    @POST("student/auth/forgot-password/confirm")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<JsonElement>

    @POST("student/auth/profile/google-link")
    suspend fun linkGoogle(@Body request: GoogleLinkRequest): Response<JsonElement>

    @DELETE("student/auth/profile/google-link")
    suspend fun unlinkGoogle(): Response<JsonElement>

    @GET("student/auth/profile")
    suspend fun getMe(): Response<JsonElement>

    @PATCH("student/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<JsonElement>

    @Multipart
    @POST("student/auth/profile/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<JsonElement>

    @GET("auth/sessions")
    suspend fun getSessions(): Response<List<MobileSessionResponse>>

    @DELETE("auth/sessions/{sessionId}")
    suspend fun revokeSession(@Path("sessionId") sessionId: String): Response<MessageResponse>
}
