package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName

data class SyncExamVersionItem(
    @SerializedName("id")
    val id: String,

    @SerializedName("exam_id")
    val examId: String,

    @SerializedName("version_code")
    val versionCode: String,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?
)
