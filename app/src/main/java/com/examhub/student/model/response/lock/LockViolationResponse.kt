package com.examhub.student.model.response.lock

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class LockViolationResponse(
    @SerializedName("violation_id")
    val violationId: String,
    @SerializedName("session_status")
    val sessionStatus: String,
    @SerializedName("auto_violated")
    val autoViolated: Boolean
)
