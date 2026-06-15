package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName
import com.examhub.student.model.response.profile.UserResponse

data class StudentRegisterResponse(
    val message: String? = null,
    @SerializedName(value = "userId", alternate = ["user_id"])
    val userId: String? = null,
    @SerializedName(value = "requiresOtp", alternate = ["requires_otp", "otp_required"])
    val requiresOtp: Boolean = false,
    @SerializedName(value = "accessToken", alternate = ["access_token"])
    val accessToken: String? = null,
    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String? = null,
    @SerializedName(value = "mustChangePassword", alternate = ["must_change_password"])
    val mustChangePassword: Boolean? = null,
    val user: UserResponse? = null
)
