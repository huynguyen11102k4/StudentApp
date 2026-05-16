package com.omr.scanner.student.model.request

data class OtpVerifyRequest(
    val email: String,
    val otpCode: String,
    val purpose: String
)
