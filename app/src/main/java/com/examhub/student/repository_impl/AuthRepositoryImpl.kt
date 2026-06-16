package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.examhub.student.model.ApiResult
import com.examhub.student.model.ApiException
import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.request.auth.ForgotPasswordRequest
import com.examhub.student.model.request.auth.GoogleLinkRequest
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.ResetPasswordRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.auth.AuthTokenResponse
import com.examhub.student.model.response.auth.GoogleLinkResponse
import com.examhub.student.model.response.common.MessageResponse
import com.examhub.student.model.response.profile.MobileSessionResponse
import com.examhub.student.model.response.auth.OtpVerifyResponse
import com.examhub.student.model.response.auth.StudentRegisterResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.AuthApiService
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.service.TokenManager
import com.examhub.student.util.helper.parseUserProfileJson
import com.examhub.student.util.helper.sanitizedStudentProfile
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
    private val authParser = AuthResponseParser(gson)

    override fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<StudentRegisterResponse>> =
        safeJsonFlow(gson, authParser::parseRegistration) {
            apiService.registerStudent(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId()))
        }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    val accessToken = result.data.accessToken
                    val refreshToken = result.data.refreshToken
                    if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                        clearUserScopedCacheIfNeeded(result.data.user)
                        tokenManager.saveTokens(accessToken, refreshToken)
                        result.data.user?.let { user ->
                            val safeUser = user.sanitizedStudentProfile()
                            tokenManager.saveCachedAvatarUrl(safeUser.avatarUrl)
                            tokenManager.saveCachedProfileJson(gson.toJson(safeUser))
                            offlineCacheManager.saveStudentIdentity(safeUser)
                        }
                    }
                }
            }

    override fun requestStudentOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.requestStudentOtp(request) }

    override fun verifyStudentOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>> =
        safeApiFlow(gson) { apiService.verifyStudentOtp(request) }

    override fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeJsonFlow(gson, authParser::parseAuthToken) {
            apiService.login(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId()))
        }
            .map(::rejectInactiveAccount)
            .onEach { result ->
                if (result is ApiResult.Success) {
                    val rawUser: UserResponse? = result.data.user
                    val safeUser = rawUser?.sanitizedStudentProfile()
                    clearUserScopedCacheIfNeeded(safeUser)
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                    safeUser?.let { user ->
                        tokenManager.saveCachedAvatarUrl(user.avatarUrl)
                        tokenManager.saveCachedProfileJson(gson.toJson(user))
                        offlineCacheManager.saveStudentIdentity(user)
                    } ?: clearUnknownUserCache()
                }
            }

    override fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>> =
        safeJsonFlow(gson, authParser::parseAuthToken) {
            apiService.loginWithGoogle(request.copy(deviceId = request.deviceId ?: tokenManager.getDeviceId()))
        }
            .map(::rejectInactiveAccount)
            .onEach { result ->
                if (result is ApiResult.Success) {
                    val rawUser: UserResponse? = result.data.user
                    val safeUser = rawUser?.sanitizedStudentProfile()
                    clearUserScopedCacheIfNeeded(safeUser)
                    tokenManager.saveTokens(result.data.accessToken, result.data.refreshToken)
                    safeUser?.let { user ->
                        tokenManager.saveCachedAvatarUrl(user.avatarUrl)
                        tokenManager.saveCachedProfileJson(gson.toJson(user))
                        offlineCacheManager.saveStudentIdentity(user)
                    } ?: clearUnknownUserCache()
                }
            }

    override fun logout(): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.logout() }
            .onEach {
                if (it is ApiResult.Success) {
                    offlineCacheManager.clearUserScopedLists()
                    tokenManager.clearTokens()
                }
            }

    override fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeApiFlow(gson) { apiService.changePassword(request) }

    override fun requestForgotPassword(request: ForgotPasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeJsonFlow(gson, MessageResponse::class.java) { apiService.requestForgotPassword(request) }

    override fun resetPassword(request: ResetPasswordRequest): Flow<ApiResult<MessageResponse>> =
        safeJsonFlow(gson, MessageResponse::class.java) { apiService.resetPassword(request) }

    override fun linkGoogle(request: GoogleLinkRequest): Flow<ApiResult<GoogleLinkResponse>> =
        safeJsonFlow(gson, { authParser.parseGoogleLink(it, expectedLinked = true) }) {
            apiService.linkGoogle(request)
        }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    updateCachedGoogleState(result.data)
                }
            }

    override fun unlinkGoogle(): Flow<ApiResult<GoogleLinkResponse>> =
        safeJsonFlow(gson, { authParser.parseGoogleLink(it, expectedLinked = false) }) {
            apiService.unlinkGoogle()
        }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    updateCachedGoogleState(result.data)
                }
            }

    override fun getMe(): Flow<ApiResult<UserResponse>> = flow {
        // Emit cached profile immediately for fast first render (stale-while-revalidate)
        val cachedProfile = tokenManager.getCachedProfileJson()?.let(gson::parseUserProfileJson)
        if (cachedProfile != null) {
            emit(ApiResult.Success(cachedProfile))
            tokenManager.saveCachedProfileJson(gson.toJson(cachedProfile))
        }

        // Always fetch fresh data from network to get latest fields (e.g. student.studentCode)
        emitAll(safeJsonFlow(gson, authParser::parseUser) { apiService.getMe() }
            .map { result ->
                if (result is ApiResult.Success) {
                    val cachedAvatar = tokenManager.getCachedAvatarUrl()
                    val safeProfile = result.data.sanitizedStudentProfile()
                    val avatarUrl = safeProfile.avatarUrl?.takeIf { it.isNotBlank() } ?: cachedAvatar
                    ApiResult.Success(safeProfile.copy(avatarUrl = avatarUrl))
                } else if (result is ApiResult.Error && cachedProfile != null) {
                    ApiResult.Success(cachedProfile)
                } else {
                    result
                }
            }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                    offlineCacheManager.saveStudentIdentity(result.data)
                }
            })
    }

    override fun updateProfile(request: UpdateProfileRequest): Flow<ApiResult<UserResponse>> =
        safeJsonFlow(gson, authParser::parseUser) { apiService.updateProfile(request) }
            .map { result ->
                if (result is ApiResult.Success) ApiResult.Success(result.data.sanitizedStudentProfile()) else result
            }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                    offlineCacheManager.saveStudentIdentity(result.data)
                }
            }

    override fun uploadAvatar(file: MultipartBody.Part): Flow<ApiResult<UserResponse>> =
        safeJsonFlow(gson, authParser::parseUser) { apiService.uploadAvatar(file) }
            .map { result ->
                if (result is ApiResult.Success) ApiResult.Success(result.data.sanitizedStudentProfile()) else result
            }
            .onEach { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveCachedAvatarUrl(result.data.avatarUrl)
                    tokenManager.saveCachedProfileJson(gson.toJson(result.data))
                    offlineCacheManager.saveStudentIdentity(result.data)
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
        val newUserId = newUser?.let { user ->
            val rawId: String? = user.id
            rawId?.takeIf { it.isNotBlank() }
        } ?: return
        val cachedUserId = tokenManager.getCachedProfileJson()
            ?.let { raw -> gson.parseUserProfileJson(raw)?.id }
        if (cachedUserId != newUserId) {
            offlineCacheManager.clearUserScopedLists()
        }
    }

    private fun updateCachedGoogleState(response: GoogleLinkResponse) {
        val cached = tokenManager.getCachedProfileJson()?.let(gson::parseUserProfileJson) ?: return
        val updated = cached.copy(
            googleLinked = response.googleLinked,
            hasPassword = response.hasPassword ?: cached.hasPassword,
            authMethods = response.authMethods ?: com.examhub.student.model.response.auth.AuthMethodsResponse(
                password = response.hasPassword ?: cached.hasPassword ?: cached.authMethods?.password ?: false,
                google = response.googleLinked
            )
        ).sanitizedStudentProfile()
        tokenManager.saveCachedProfileJson(gson.toJson(updated))
        offlineCacheManager.saveStudentIdentity(updated)
    }

    private fun rejectInactiveAccount(
        result: ApiResult<AuthTokenResponse>
    ): ApiResult<AuthTokenResponse> {
        val user = (result as? ApiResult.Success)?.data?.user
        return if (user?.isActive == false) {
            ApiResult.Error(
                ApiException(
                    code = "ACCOUNT_INACTIVE",
                    message = "Account is inactive"
                )
            )
        } else {
            result
        }
    }

    private fun clearUnknownUserCache() {
        offlineCacheManager.clearUserScopedLists()
        tokenManager.saveCachedAvatarUrl(null)
        tokenManager.saveCachedProfileJson(null)
        tokenManager.saveCachedSessionsJson(null)
    }
}
