package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class SyncPullResponse(
    @SerializedName("exams")
    val exams: List<SyncExamItem>? = null,

    @SerializedName("templates")
    val templates: List<SyncTemplateItem>? = null,

    @SerializedName("exam_versions")
    val examVersions: List<SyncExamVersionItem>? = null
)

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

data class SyncTemplateItem(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("version")
    val version: String?,

    @SerializedName("base_image_url")
    val baseImageUrl: String?,

    @SerializedName("anchor_points")
    val anchorPoints: JsonElement?,

    @SerializedName("grid_config")
    val gridConfig: JsonObject?,

    @SerializedName("template_type")
    val templateType: String?,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?
)

data class SyncExamVersionItem(
    @SerializedName("id")
    val id: String,

    @SerializedName("exam_id")
    val examId: String,

    @SerializedName("version_code")
    val versionCode: String,

    @SerializedName("answer_key")
    val answerKey: JsonObject?,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?
)
