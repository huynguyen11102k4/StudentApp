package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealStudentResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("code")
    val code: String
)
