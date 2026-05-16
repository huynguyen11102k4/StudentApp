package com.omr.scanner.student.model.response

data class ApiErrorBody(
    val code: String?,
    val message: String?,
    val details: List<String>?
)
