package com.examhub.student.model.response.common


data class ApiErrorBody(
    val code: String?,
    val message: String?,
    val details: List<String>?
)
