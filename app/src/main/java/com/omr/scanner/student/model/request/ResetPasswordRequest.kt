package com.omr.scanner.student.model.request

data class ResetPasswordRequest(
    val email: String,
    val otpCode: String,
    val newPassword: String
)
