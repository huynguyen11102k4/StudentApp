package com.examhub.student.model.request

import com.google.gson.annotations.SerializedName

data class GoogleLoginRequest(
    val email: String? = null,
    @SerializedName("google_id_token")
    val googleIdToken: String,
    @SerializedName("device_id")
    val deviceId: String? = null
)
