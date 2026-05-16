package com.omr.scanner.student.model.request

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
