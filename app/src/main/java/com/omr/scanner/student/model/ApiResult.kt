package com.omr.scanner.student.model

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: ApiException) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
}
