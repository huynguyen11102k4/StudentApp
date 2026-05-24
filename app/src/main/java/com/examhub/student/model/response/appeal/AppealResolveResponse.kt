package com.examhub.student.model.response.appeal

import com.google.gson.annotations.SerializedName

data class AppealResolveResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("teacher_note")
    val teacherNote: String?,

    @SerializedName("items")
    val items: List<AppealResolvedItemResponse>? = null
)
