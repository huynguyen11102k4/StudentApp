package com.examhub.student.model

data class ApiException(
    val code: String,
    override val message: String,
    val httpCode: Int? = null,
    val details: List<String> = emptyList(),
    val causeThrowable: Throwable? = null
) : Exception(message, causeThrowable)
