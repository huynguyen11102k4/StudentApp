package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StartedTemplateResponse(
    val id: String? = null,
    @SerializedName("grid_config")
    val gridConfig: JsonElement? = null,
    @SerializedName("base_image_url")
    val baseImageUrl: String? = null,
    @SerializedName("preview_image_url")
    val previewImageUrl: String? = null
)
