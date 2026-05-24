package com.examhub.student.model.response.sync

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import com.google.gson.JsonObject

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
