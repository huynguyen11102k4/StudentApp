package com.examhub.student.model.request.lock

import com.google.gson.annotations.SerializedName

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
