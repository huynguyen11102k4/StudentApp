package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealSheetResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double?,

    @SerializedName(value = "student", alternate = ["student_info", "studentInfo"])
    val student: AppealStudentResponse? = null,

    @SerializedName("exam")
    val exam: AppealExamResponse? = null
)
