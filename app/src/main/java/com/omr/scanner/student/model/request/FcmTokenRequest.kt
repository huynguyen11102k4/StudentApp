package com.omr.scanner.student.model.request

import com.google.gson.annotations.SerializedName

data class FcmTokenRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,
    @SerializedName("app_version")
    val appVersion: String? = null
)
