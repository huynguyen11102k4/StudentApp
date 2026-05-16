package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiException
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.ApiEnvelope
import com.omr.scanner.student.model.response.ApiErrorEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException

fun <T> safeApiFlow(
    gson: Gson,
    apiCall: suspend () -> Response<T>
): Flow<ApiResult<T>> = flow {
    emit(ApiResult.Loading)
    val response = apiCall()
    emit(handleResponse(gson, response))
}.catch { throwable ->
    emit(ApiResult.Error(throwable.toApiException()))
}

fun <T> safeEnvelopeFlow(
    gson: Gson,
    apiCall: suspend () -> Response<ApiEnvelope<T>>
): Flow<ApiResult<T>> = flow {
    emit(ApiResult.Loading)
    val response = apiCall()
    if (response.isSuccessful) {
        val body = response.body()?.data
        emit(
            if (body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Error(ApiException("EMPTY_BODY", "Response body is empty", response.code()))
            }
        )
    } else {
        emit(ApiResult.Error(parseApiException(gson, response)))
    }
}.catch { throwable ->
    emit(ApiResult.Error(throwable.toApiException()))
}

private fun <T> handleResponse(gson: Gson, response: Response<T>): ApiResult<T> {
    if (response.isSuccessful) {
        val body = response.body()
        return if (body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Error(ApiException("EMPTY_BODY", "Response body is empty", response.code()))
        }
    }
    return ApiResult.Error(parseApiException(gson, response))
}

private fun parseApiException(gson: Gson, response: Response<*>): ApiException {
    val rawError = response.errorBody()?.string()
    val parsed = rawError?.let {
        runCatching { gson.fromJson(it, ApiErrorEnvelope::class.java) }.getOrNull()
    }?.error

    return ApiException(
        code = parsed?.code ?: response.code().toString(),
        message = parsed?.message ?: response.message(),
        httpCode = response.code(),
        details = parsed?.details.orEmpty()
    )
}

private fun Throwable.toApiException(): ApiException {
    if (this is ApiException) return this
    return when (this) {
        is IOException -> ApiException("NETWORK_ERROR", message ?: "Network error", causeThrowable = this)
        else -> ApiException("UNKNOWN_ERROR", message ?: "Unknown error", causeThrowable = this)
    }
}
