package com.examhub.student.model.request.lock

import com.google.gson.annotations.SerializedName

data class LockValidateSessionRequest(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("device_id")
    val deviceId: String
)
