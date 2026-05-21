package com.examhub.student.model.response

data class ApiErrorBody(
    val code: String?,
    val message: String?,
    val details: List<String>?
)
