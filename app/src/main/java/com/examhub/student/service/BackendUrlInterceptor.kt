package com.examhub.student.service

import com.examhub.student.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class BackendUrlInterceptor(
    private val backendUrlManager: BackendUrlManager
) : Interceptor {
    private val defaultBaseUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val overrideBaseUrl = backendUrlManager.currentHttpUrl()
            ?: return chain.proceed(request)

        val originalUrl = request.url
        val shouldRewrite = originalUrl.scheme == defaultBaseUrl.scheme &&
            originalUrl.host == defaultBaseUrl.host &&
            originalUrl.port == defaultBaseUrl.port

        if (!shouldRewrite) return chain.proceed(request)

        val defaultPathSegments = defaultBaseUrl.encodedPathSegments.filter(String::isNotBlank)
        val originalPathSegments = originalUrl.encodedPathSegments.filter(String::isNotBlank)
        val relativePathSegments = if (originalPathSegments.take(defaultPathSegments.size) == defaultPathSegments) {
            originalPathSegments.drop(defaultPathSegments.size)
        } else {
            originalPathSegments
        }

        val builder = overrideBaseUrl.newBuilder()
        relativePathSegments.forEach(builder::addEncodedPathSegment)
        originalUrl.query?.let(builder::encodedQuery)

        return chain.proceed(
            request.newBuilder()
                .url(builder.build())
                .build()
        )
    }
}
