package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StudentAppealCreateResponse(
    @SerializedName("appeal_id")
    val appealId: String,
    val status: String,
    @SerializedName("created_at")
    val createdAt: String
)
