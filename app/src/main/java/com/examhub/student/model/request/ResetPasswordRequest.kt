package com.examhub.student.model.request

import com.google.gson.annotations.SerializedName

data class ResetPasswordRequest(
    val email: String,
    @SerializedName("otp_code")
    val otpCode: String,
    @SerializedName("new_password")
    val newPassword: String
)
