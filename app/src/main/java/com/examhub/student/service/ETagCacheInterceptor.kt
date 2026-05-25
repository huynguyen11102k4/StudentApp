package com.examhub.student.service

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class ETagCacheInterceptor(
    private val cacheManager: ETagCacheManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.method != "GET") return chain.proceed(originalRequest)
        if (shouldBypassConditionalCache(originalRequest.url.encodedPath)) {
            return chain.proceed(originalRequest)
        }

        val url = originalRequest.url.toString()
        val cached = cacheManager.get(url)
        val request = if (cached != null) {
            originalRequest.newBuilder()
                .header("If-None-Match", cached.etag)
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)
        if (response.code == 304 && cached != null) {
            response.close()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(cached.code)
                .message(cached.message)
                .header("ETag", cached.etag)
                .header("X-Cache-Hit", "true")
                .body(cached.body.toResponseBody(cached.contentType?.toMediaTypeOrNull()))
                .build()
        }

        val etag = response.header("ETag")
        val responseBody = response.body
        if (response.isSuccessful && etag != null && responseBody != null) {
            val contentType = responseBody.contentType()
            val bodyString = responseBody.string()
            cacheManager.put(url, etag, bodyString, contentType?.toString(), response.code, response.message)
            return response.newBuilder()
                .body(bodyString.toResponseBody(contentType))
                .build()
        }

        return response
    }

    private fun shouldBypassConditionalCache(path: String): Boolean {
        return path.endsWith("/auth/me") ||
            path.endsWith("/auth/sessions") ||
            path.contains("/student/results") ||
            path.contains("/student/appeals")
    }
}
