package com.examhub.student.model.response.template

import com.google.gson.annotations.SerializedName

data class AnchorPointResponse(
    val id: Int? = null,
    @SerializedName(value = "abs_x", alternate = ["absX"])
    val absX: Float,
    @SerializedName(value = "abs_y", alternate = ["absY"])
    val absY: Float,
    val u: Float,
    val v: Float
)
