package com.examhub.student.model.response.template

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class OmrTemplateResponse(
    val id: String? = null,
    val name: String? = null,
    @SerializedName("template_type")
    val templateType: String? = null,
    @SerializedName("student_code_type")
    val studentCodeType: String? = null,
    @SerializedName("identification_mode")
    val identificationMode: String? = null,
    @SerializedName("anchor_points")
    val anchorPoints: JsonElement? = null,
    @SerializedName("grid_config")
    val gridConfig: JsonElement? = null,
    @SerializedName("base_image_url")
    val baseImageUrl: String? = null,
    @SerializedName("preview_image_url")
    val previewImageUrl: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
) : Serializable
