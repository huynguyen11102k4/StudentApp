package com.examhub.student.model.response

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class AnswerKeyVersionResponse(
    @SerializedName("version_code")
    val versionCode: String,
    @SerializedName("answer_key")
    val answerKey: Map<String, JsonElement>
)
