package com.examhub.student.model.response.submission

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StudentSubmitResponse(
    @SerializedName("submission_id")
    val submissionId: String,
    val status: String,
    @SerializedName("submitted_at")
    val submittedAt: String,
    @SerializedName("session_status")
    val sessionStatus: String
)
