package com.examhub.student.model.response.common

import com.google.gson.annotations.SerializedName

data class OfflineSubmissionPermitResponse(
    val permit: String? = null,
    @SerializedName(value = "deadline_at", alternate = ["deadlineAt"])
    val deadlineAt: String? = null,
    @SerializedName(value = "sync_deadline_at", alternate = ["syncDeadlineAt"])
    val syncDeadlineAt: String? = null,
    @SerializedName(value = "requires_client_submission_id", alternate = ["requiresClientSubmissionId"])
    val requiresClientSubmissionId: Boolean = false
)
