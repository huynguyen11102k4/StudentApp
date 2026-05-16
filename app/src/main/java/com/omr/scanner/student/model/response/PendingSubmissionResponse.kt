package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PendingSubmissionResponse(
    val id: String,
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("raw_image_url")
    val rawImageUrl: String?,
    @SerializedName("dewarped_image_url")
    val dewarpedImageUrl: String?,
    @SerializedName("processed_image_url")
    val processedImageUrl: String? = null,
    @SerializedName("total_score")
    val totalScore: Double? = null,
    val status: String,
    @SerializedName("student_code")
    val studentCode: String?,
    @SerializedName("student_name")
    val studentName: String?,
    @SerializedName("exam_name")
    val examName: String?,
    @SerializedName("submitted_at")
    val submittedAt: String?
) : Serializable
