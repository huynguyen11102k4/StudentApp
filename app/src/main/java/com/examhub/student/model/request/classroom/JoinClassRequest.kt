package com.examhub.student.model.request.classroom

import com.google.gson.annotations.SerializedName

data class JoinClassRequest(
    val joinCode: String,
    @SerializedName("studentCode")
    val studentCode: String
)
