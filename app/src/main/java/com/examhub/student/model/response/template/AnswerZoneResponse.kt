package com.examhub.student.model.response.template

import com.google.gson.annotations.SerializedName

data class AnswerZoneResponse(
    @SerializedName(value = "start_number", alternate = ["startNumber"])
    val startNumber: Int = 1,
    @SerializedName(value = "end_number", alternate = ["endNumber"])
    val endNumber: Int = 0,
    val rows: Int? = null,
    val layout: AnswerZoneLayoutResponse? = null,
    @SerializedName(value = "options_per_question", alternate = ["optionsPerQuestion"])
    val optionsPerQuestion: Int = 4,
    val cols: Int = 1,
    val direction: String = "vertical",
    @SerializedName(value = "bounding_box", alternate = ["boundingBox"])
    val boundingBox: BoundingBoxResponse
)

data class AnswerZoneLayoutResponse(
    val rows: Int? = null,
    val options: Int? = null,
    @SerializedName(value = "start_question_index", alternate = ["startQuestionIndex"])
    val startQuestionIndex: Int? = null
)
