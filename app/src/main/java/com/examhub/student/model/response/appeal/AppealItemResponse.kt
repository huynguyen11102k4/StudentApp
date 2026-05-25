package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealItemResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName(value = "question_number", alternate = ["questionNumber"])
    val questionNumber: Int,

    @SerializedName(value = "student_message", alternate = ["studentMessage"])
    val studentMessage: String?,

    @SerializedName(value = "teacher_response", alternate = ["teacherResponse"])
    val teacherResponse: String?,

    @SerializedName("status")
    val status: String
)
