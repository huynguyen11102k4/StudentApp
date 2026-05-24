package com.examhub.student.model.request.lock

import com.google.gson.annotations.SerializedName

data class LockHeartbeatRequest(
    @SerializedName("battery_level")
    val batteryLevel: Int? = null,
    val network: Map<String, String>? = null,
    @SerializedName("app_in_foreground")
    val appInForeground: Boolean = true
)
