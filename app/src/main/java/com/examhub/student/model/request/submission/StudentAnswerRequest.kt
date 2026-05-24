package com.examhub.student.model.request.submission

import com.google.gson.annotations.SerializedName

data class StudentAnswerRequest(
    @SerializedName("question_number")
    val questionNumber: Int,
    val answer: String?
)
