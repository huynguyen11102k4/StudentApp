package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName
import com.examhub.student.model.response.profile.UserResponse

data class AuthTokenResponse(
    @SerializedName(value = "accessToken", alternate = ["access_token"])
    val accessToken: String,
    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String,
    @SerializedName(value = "mustChangePassword", alternate = ["must_change_password"])
    val mustChangePassword: Boolean? = null,
    val user: UserResponse? = null
)
