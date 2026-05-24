package com.examhub.student.model.request.auth

import com.google.gson.annotations.SerializedName

data class OtpVerifyRequest(
    val email: String,
    @SerializedName("otp_code")
    val otpCode: String,
    @SerializedName("type")
    val purpose: String = "register"
)
