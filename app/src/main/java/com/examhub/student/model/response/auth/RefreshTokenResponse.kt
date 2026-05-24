package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName
import com.examhub.student.model.response.profile.UserResponse

data class RefreshTokenResponse(
    @SerializedName(value = "accessToken", alternate = ["access_token"])
    val accessToken: String,
    @SerializedName(value = "refreshToken", alternate = ["refresh_token"])
    val refreshToken: String,
    // API doc also returns a user object on refresh
    val user: UserResponse? = null
)
