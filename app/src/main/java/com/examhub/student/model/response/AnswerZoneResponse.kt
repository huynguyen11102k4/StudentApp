package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class AnswerZoneResponse(
    @SerializedName(value = "start_number", alternate = ["startNumber"])
    val startNumber: Int,
    @SerializedName(value = "end_number", alternate = ["endNumber"])
    val endNumber: Int,
    @SerializedName(value = "options_per_question", alternate = ["optionsPerQuestion"])
    val optionsPerQuestion: Int,
    val cols: Int,
    val direction: String,
    @SerializedName(value = "bounding_box", alternate = ["boundingBox"])
    val boundingBox: BoundingBoxResponse
)
