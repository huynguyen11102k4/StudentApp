package com.examhub.student.service

import com.examhub.student.model.request.RefreshTokenRequest
import com.examhub.student.model.response.RefreshTokenResponse
import okhttp3.Authenticator
import okhttp3.Route
import okhttp3.Response
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val refreshService: RefreshTokenService
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): okhttp3.Request? {
        if (responseCount(response) >= 2) return null

        val refreshToken = tokenManager.getRefreshToken() ?: return null
        val refreshResponse = refreshService.refreshToken(RefreshTokenRequest(refreshToken)).execute()
        if (!refreshResponse.isSuccessful) {
            tokenManager.clearTokens(notifySessionExpired = true)
            return null
        }

        val tokens = refreshResponse.body() ?: return null
        tokenManager.saveTokens(tokens.accessToken, tokens.refreshToken)

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${tokens.accessToken}")
            .header("X-Device-Id", tokenManager.getDeviceId())
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}

interface RefreshTokenService {
    @POST("student/auth/refresh")
    fun refreshToken(@Body request: RefreshTokenRequest): Call<RefreshTokenResponse>
}
