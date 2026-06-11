package com.examhub.student.service

import okhttp3.Interceptor
import okhttp3.Response

class UnauthorizedInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 && shouldExpireSession(request.url.encodedPath)) {
            tokenManager.clearTokens(notifySessionExpired = true)
        }
        return response
    }

    private fun shouldExpireSession(path: String): Boolean {
        return !path.endsWith("/student/auth/login") &&
            !path.endsWith("/student/auth/register") &&
            !path.contains("/student/auth/otp") &&
            !path.contains("/student/auth/forgot-password")
    }
}
