package com.examhub.student.model.request.appeal

import com.google.gson.annotations.SerializedName

data class StudentAppealItemRequest(
    @SerializedName("question_number")
    val questionNumber: Int,
    @SerializedName("student_message")
    val studentMessage: String
)
