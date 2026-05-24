package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.AuthApiService
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okhttp3.MultipartBody

class AuthRepositoryImpl(
    private val apiService: AuthApiService,
    private val tokenManager: TokenManager,
    private val offlineCacheManager: OfflineCacheManager,
    private val gson: Gson
) : AuthRepository {
    override fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<StudentRegisterResponse>> =
        safeApiFlow(gson) { apiService.registerStudent(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    val accessToken = result.data.accessToken
                    val refreshToken = result.data.refreshToken
                    if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                        clearUserScopedCacheIfNeeded(result.data.user)
                        tokenManager.saveTokens(accessToken, refreshToken)
                        result.data.user?.let { user ->
                            tokenManager.saveCachedAvatarUrl(user.avatarUrl)
                            tokenManager.saveCachedProfileJson(gson.toJson(user))
                        }
                    }
                }
            }

    override fun requestStudentOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.requestStudentOtp(request) }

    override fun verifyStudentOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>> =
        safeApiFlow(gson) { apiService.verifyStudentOtp(request) }

    override fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeApiFlow(gson) { apiService.login(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    clearUserScopedCacheIfNeeded(result.data.user)
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                    tokenManager.saveCachedAvatarUrl(result.data.user.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data.user))
                }
            }

    override fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeApiFlow(gson) { apiService.loginWithGoogle(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId())) }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    clearUserScopedCacheIfNeeded(result.data.user)
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                    tokenManager.saveCachedAvatarUrl(result.data.user.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data.user))
                }
            }

    override fun logout(): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.logout() }
            .onEach { if (it is ApiResult.Success) tokenManager.clearTokens() }

    override fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.changePassword(request) }

    override fun getMe(): Flow<ApiResult<UserResponse>> = flow {
        // Emit cached profile immediately for fast first render (stale-while-revalidate)
        val cachedProfile = tokenManager.getCachedProfileJson()
            ?.let { json -> runCatching { gson.fromJson(json, UserResponse::class.java) }.getOrNull() }
        if (cachedProfile != null) {
            emit(ApiResult.Success(cachedProfile))
        }

        // Always fetch fresh data from network to get latest fields (e.g. student.studentCode)
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

    private fun clearUserScopedCacheIfNeeded(newUser: UserResponse?) {
        val newUserId = newUser?.id?.takeIf { it.isNotBlank() } ?: return
        val cachedUserId = tokenManager.getCachedProfileJson()
            ?.let { raw -> runCatching { gson.fromJson(raw, UserResponse::class.java).id }.getOrNull() }
        if (cachedUserId != newUserId) {
            offlineCacheManager.clearUserScopedLists()
        }
    }

}
