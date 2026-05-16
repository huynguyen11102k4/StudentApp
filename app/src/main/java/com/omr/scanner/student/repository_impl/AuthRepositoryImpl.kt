package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import com.omr.scanner.student.repository.AuthRepository
import com.omr.scanner.student.service.AuthApiService
import com.omr.scanner.student.service.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okhttp3.MultipartBody

class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val tokenManager: TokenManager,
    private val gson: Gson
) : AuthRepository {
    override fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeApiFlow(gson) { apiService.registerStudent(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                }
            }

    override fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeApiFlow(gson) { apiService.login(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                }
            }

    override fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeApiFlow(gson) { apiService.loginWithGoogle(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                }
            }

    override fun logout(): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.logout() }
            .onEach { if (it is ApiResult.Success) tokenManager.clearTokens() }

    override fun logoutAll(): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.logoutAll() }
            .onEach { if (it is ApiResult.Success) tokenManager.clearTokens() }

    override fun requestOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.requestOtp(request) }

    override fun verifyOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>> =
        safeApiFlow(gson) { apiService.verifyOtp(request) }

    override fun requestForgotPassword(request: ForgotPasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.requestForgotPassword(request) }

    override fun resetPassword(request: ResetPasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.resetPassword(request) }

    override fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.changePassword(request) }

    override fun getMe(): Flow<ApiResult<UserResponse>> = flow {
        tokenManager.getCachedProfileJson()
            ?.let { json -> runCatching { gson.fromJson(json, UserResponse::class.java) }.getOrNull() }
            ?.let { cached -> emit(ApiResult.Success(cached)) }

        emitAll(safeApiFlow(gson) { apiService.getMe() }
            .map { result ->
                if (result is ApiResult.Success) {
                    val cachedAvatar = tokenManager.getCachedAvatarUrl()
                    val avatarUrl = result.data.avatarUrl?.takeIf { it.isNotBlank() } ?: cachedAvatar
                    if (avatarUrl != null && avatarUrl != result.data.avatarUrl) {
                        ApiResult.Success(result.data.copy(avatarUrl = avatarUrl))
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                }
            })
    }

    override fun updateProfile(request: UpdateProfileRequest): Flow<ApiResult<UserResponse>> =
        safeApiFlow(gson) { apiService.updateProfile(request) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                }
            }

    override fun uploadAvatar(file: MultipartBody.Part): Flow<ApiResult<UserResponse>> =
        safeApiFlow(gson) { apiService.uploadAvatar(file) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                }
            }

    override fun getSessions(): Flow<ApiResult<List<MobileSessionResponse>>> = flow {
        tokenManager.getCachedSessionsJson()
            ?.let { json ->
                runCatching {
                    gson.fromJson<List<MobileSessionResponse>>(
                        json,
                        object : TypeToken<List<MobileSessionResponse>>() {}.type
                    )
                }.getOrNull()
            }
            ?.let { cached -> emit(ApiResult.Success(cached)) }

        emitAll(safeApiFlow(gson) { apiService.getSessions() }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedSessionsJson(gson.toJson(result.data))
                }
            })
    }

    override fun revokeSession(sessionId: String): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.revokeSession(sessionId) }
}
