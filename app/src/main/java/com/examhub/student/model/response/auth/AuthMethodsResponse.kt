package com.examhub.student.model.response.auth

data class AuthMethodsResponse(
    val password: Boolean = false,
    val google: Boolean = false
)
