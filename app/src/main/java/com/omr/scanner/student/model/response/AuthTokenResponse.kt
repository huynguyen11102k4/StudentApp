package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName

data class AuthTokenResponse(
    @SerializedName(value = "accessToken", alternate = ["access_token"])
    val accessToken: String,
    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String,
    @SerializedName("must_change_password")
    val mustChangePassword: Boolean? = null,
    val user: UserResponse
)
