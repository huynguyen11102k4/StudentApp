package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealStudentResponse(
    @SerializedName("id")
    val id: String? = null,

    @SerializedName(value = "name", alternate = ["full_name", "fullName"])
    val name: String? = null,

    @SerializedName(value = "code", alternate = ["student_code", "studentCode"])
    val code: String? = null
)
