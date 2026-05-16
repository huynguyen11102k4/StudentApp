package com.omr.scanner.student.model.request

import com.google.gson.annotations.SerializedName

data class LockValidateSessionRequest(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("device_id")
    val deviceId: String
)

data class LockViolationRequest(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("violation_type")
    val violationType: String,
    @SerializedName("occurred_at")
    val occurredAt: String,
    @SerializedName("evidence_data")
    val evidenceData: Map<String, Any?> = emptyMap()
)

data class LockHeartbeatRequest(
    @SerializedName("battery_level")
    val batteryLevel: Int? = null,
    val network: Map<String, String>? = null,
    @SerializedName("app_in_foreground")
    val appInForeground: Boolean = true
)
