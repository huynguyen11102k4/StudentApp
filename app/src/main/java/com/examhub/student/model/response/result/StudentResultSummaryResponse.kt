package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StudentResultSummaryResponse(
    val id: String,
    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double? = null,
    @SerializedName(value = "processed_image_url", alternate = ["processedImageUrl"])
    val processedImageUrl: String? = null,
    val source: String? = null,
    @SerializedName(value = "graded_at", alternate = ["gradedAt"])
    val gradedAt: String? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    val exam: StudentResultExamResponse? = null
)
