package com.examhub.student.model.response.lock

import com.google.gson.annotations.SerializedName

data class LockViolationSyncResponse(
    val synced: Int,
    val failed: Int,
    val results: List<LockViolationSyncResultResponse> = emptyList()
)

data class LockViolationSyncResultResponse(
    @SerializedName("client_event_id")
    val clientEventId: String,
    val status: String,
    @SerializedName("violation_id")
    val violationId: String? = null,
    val error: String? = null
)
