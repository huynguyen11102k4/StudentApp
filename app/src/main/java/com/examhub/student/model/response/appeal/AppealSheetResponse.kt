package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealSheetResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double?,

    @SerializedName(value = "processed_image_url", alternate = ["processedImageUrl"])
    val processedImageUrl: String?,

    @SerializedName(value = "dewarped_image_url", alternate = ["dewarpedImageUrl"])
    val dewarpedImageUrl: String?,

    @SerializedName("exam")
    val exam: AppealExamResponse? = null
)
