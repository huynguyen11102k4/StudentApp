package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealResolvedItemResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("teacher_response")
    val teacherResponse: String?
)
