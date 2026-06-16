package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName

data class AuthMethodsResponse(
    @SerializedName(value = "password", alternate = ["has_password", "password_enabled", "passwordEnabled"])
    val password: Boolean = false,
    @SerializedName(value = "google", alternate = ["google_linked", "google_enabled", "googleEnabled"])
    val google: Boolean = false
)
