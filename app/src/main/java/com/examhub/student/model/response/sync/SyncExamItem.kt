package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class SyncExamItem(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("subject")
    val subject: String?,

    @SerializedName("duration")
    val duration: Int?,

    @SerializedName("total_questions")
    val totalQuestions: Int?,

    @SerializedName("status")
    val status: String,

    @SerializedName("exam_type")
    val examType: String?,

    @SerializedName("template_id")
    val templateId: String?,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?
)
