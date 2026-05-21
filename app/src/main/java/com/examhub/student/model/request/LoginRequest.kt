package com.examhub.student.model.request

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String,
    @SerializedName("device_id")
    val deviceId: String? = null
)
