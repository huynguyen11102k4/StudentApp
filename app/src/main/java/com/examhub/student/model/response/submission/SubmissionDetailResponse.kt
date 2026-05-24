package com.examhub.student.model.response.submission

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class SubmissionDetailResponse(
    val id: String,
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("raw_image_url")
    val rawImageUrl: String?,
    @SerializedName("dewarped_image_url")
    val dewarpedImageUrl: String?,
    val status: String,
    @SerializedName("image_quality_score")
    val imageQualityScore: Float?,
    @SerializedName("quality_feedback")
    val qualityFeedback: JsonObject?,
    @SerializedName("rejection_reason")
    val rejectionReason: String?,
    @SerializedName("submitted_at")
    val submittedAt: String?,
    val student: SubmissionStudentResponse?,
    val exam: SubmissionExamResponse?
)
