package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealItemResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("question_number")
    val questionNumber: Int,

    @SerializedName("student_message")
    val studentMessage: String?,

    @SerializedName("teacher_response")
    val teacherResponse: String?,

    @SerializedName("status")
    val status: String
)
