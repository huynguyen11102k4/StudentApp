package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealSheetResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("total_score")
    val totalScore: Double?,

    @SerializedName("processed_image_url")
    val processedImageUrl: String?,

    @SerializedName("dewarped_image_url")
    val dewarpedImageUrl: String?
)
