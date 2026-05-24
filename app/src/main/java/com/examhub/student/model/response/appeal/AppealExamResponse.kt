package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealExamResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("subject")
    val subject: String?
)
