package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class SubmissionStudentResponse(
    @SerializedName("student_code")
    val studentCode: String,
    @SerializedName("full_name")
    val fullName: String
)
