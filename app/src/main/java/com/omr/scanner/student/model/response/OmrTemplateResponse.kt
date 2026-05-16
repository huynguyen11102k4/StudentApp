package com.omr.scanner.student.model.response

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class OmrTemplateResponse(
    val id: String,
    val name: String,
    @SerializedName(value = "anchor_points", alternate = ["anchorPoints"])
    val anchorPoints: JsonElement? = null,
    @SerializedName(value = "grid_config", alternate = ["gridConfig"])
    val gridConfig: JsonElement? = null
) : Serializable
