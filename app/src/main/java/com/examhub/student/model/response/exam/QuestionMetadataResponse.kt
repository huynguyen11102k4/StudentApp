package com.examhub.student.model.response.exam

import com.google.gson.annotations.SerializedName

data class QuestionMetadataResponse(
    @SerializedName("question_number")
    val questionNumber: Int,
    @SerializedName("point_value")
    val pointValue: Float,
    val topic: String?
)
