package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName
import com.examhub.student.model.response.profile.UserResponse

data class StudentRegisterResponse(
    val message: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("requires_otp")
    val requiresOtp: Boolean = false,
    @SerializedName(value = "accessToken", alternate = ["access_token"])
    val accessToken: String? = null,
    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String? = null,
    @SerializedName("must_change_password")
    val mustChangePassword: Boolean? = null,
    val user: UserResponse? = null
)
