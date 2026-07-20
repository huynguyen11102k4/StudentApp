package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.examhub.student.OmrApplication
import com.examhub.student.R
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.common.ApiErrorEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.lang.reflect.Type
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

fun <T> safeJsonFlow(
    gson: Gson,
    responseType: Type,
    apiCall: suspend () -> Response<JsonElement>
): Flow<ApiResult<T>> = flow {
    emit(ApiResult.Loading)
    val response = apiCall()
    if (response.isSuccessful) {
        val root = response.body()
        val payload = root.extractPayload()
        if (payload == null) {
            emit(ApiResult.Error(ApiException("EMPTY_BODY", "Response body is empty", response.code())))
        } else {
            emit(ApiResult.Success(gson.fromJson(payload, responseType)))
        }
    } else {
        emit(ApiResult.Error(parseApiException(gson, response)))
    }
}.catch { throwable ->
    emit(ApiResult.Error(throwable.toApiException()))
}

fun <T> safeJsonFlow(
    gson: Gson,
    parser: (JsonElement) -> T,
    apiCall: suspend () -> Response<JsonElement>
): Flow<ApiResult<T>> = flow {
    emit(ApiResult.Loading)
    val response = apiCall()
    if (response.isSuccessful) {
        val root = response.body()
        if (root == null || root.isJsonNull) {
            emit(ApiResult.Error(ApiException("EMPTY_BODY", "Response body is empty", response.code())))
        } else {
            emit(ApiResult.Success(parser(root)))
        }
    } else {
        emit(ApiResult.Error(parseApiException(gson, response)))
    }
}.catch { throwable ->
    emit(ApiResult.Error(throwable.toApiException()))
}

private fun JsonElement?.extractPayload(): JsonElement? {
    val root = this ?: return null
    if (!root.isJsonObject) return root
    val obj = root.asJsonObject
    val data = obj.get("data")
    return if (data != null && !data.isJsonNull) data else root
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
    val root = rawError?.let {
        runCatching { gson.fromJson(it, JsonElement::class.java) }.getOrNull()
    }
    val parsed = rawError?.let {
        runCatching { gson.fromJson(it, ApiErrorEnvelope::class.java) }.getOrNull()
    }?.error
    val fallbackObject = root?.takeIf { it.isJsonObject }?.asJsonObject
    val nestedError = fallbackObject?.get("error")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
    val code = parsed?.code
        ?: nestedError?.get("code")?.takeIf { it.isJsonPrimitive }?.asString
        ?: fallbackObject?.get("code")?.takeIf { it.isJsonPrimitive }?.asString
    val message = parsed?.message
        ?: nestedError?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
        ?: fallbackObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
    val details = parsed?.details
        ?: nestedError?.get("details").toStringListOrNull()
        ?: fallbackObject?.get("details").toStringListOrNull()

    return ApiException(
        code = code ?: response.code().toString(),
        message = message ?: response.message(),
        httpCode = response.code(),
        details = details.orEmpty()
    )
}

private fun JsonElement?.toStringListOrNull(): List<String>? {
    if (this == null || isJsonNull) return null
    return when {
        isJsonArray -> asJsonArray.mapNotNull { element ->
            when {
                element.isJsonPrimitive -> element.asString
                element.isJsonObject -> element.toString()
                else -> null
            }
        }
        isJsonPrimitive -> listOf(asString)
        isJsonObject -> listOf(toString())
        else -> null
    }
}

private fun Throwable.toApiException(): ApiException {
    if (this is ApiException) return this
    return when (this) {
        is IOException -> ApiException(
            "NETWORK_ERROR",
            OmrApplication.appContext.getString(R.string.common_no_internet),
            causeThrowable = this
        )
        is JsonParseException -> ApiException(
            "PARSE_ERROR",
            OmrApplication.appContext.getString(R.string.api_invalid_server_format),
            causeThrowable = this
        )
        else -> ApiException("UNKNOWN_ERROR", message ?: "Unknown error", causeThrowable = this)
    }
}
