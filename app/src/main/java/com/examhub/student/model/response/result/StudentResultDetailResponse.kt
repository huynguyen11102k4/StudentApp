package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class StudentResultDetailResponse(
    val id: String,
    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double? = null,
    @SerializedName(value = "processed_image_url", alternate = ["processedImageUrl"])
    val processedImageUrl: String? = null,
    @SerializedName(value = "raw_image_url", alternate = ["rawImageUrl"])
    val rawImageUrl: String? = null,
    @SerializedName(value = "dewarped_image_url", alternate = ["dewarpedImageUrl"])
    val dewarpedImageUrl: String? = null,
    val source: String? = null,
    @SerializedName(value = "graded_at", alternate = ["gradedAt"])
    val gradedAt: String? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    val exam: StudentResultExamResponse? = null,
    @SerializedName(value = "answer_details", alternate = ["answerDetails"])
    val answerDetails: List<StudentResultAnswerResponse> = emptyList()
)
