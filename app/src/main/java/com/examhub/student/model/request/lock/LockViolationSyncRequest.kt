package com.examhub.student.model.request.lock

import com.google.gson.annotations.SerializedName

data class LockViolationSyncRequest(
    val events: List<LockViolationSyncItemRequest>
)

data class LockViolationSyncItemRequest(
    @SerializedName("client_event_id")
    val clientEventId: String,
    @SerializedName("violation_type")
    val violationType: String,
    @SerializedName("occurred_at")
    val occurredAt: String,
    @SerializedName("evidence_data")
    val evidenceData: Map<String, Any?> = emptyMap()
)
