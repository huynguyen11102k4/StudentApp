package com.examhub.student.model.response.auth


data class OtpVerifyResponse(
    val message: String,
    val verified: Boolean
)
