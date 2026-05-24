package com.examhub.student.model.response.template

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class GridConfigResponse(
    @SerializedName(value = "fixed_zones", alternate = ["fixedZones"])
    val fixedZones: JsonElement? = null,
    @SerializedName(value = "id_zones", alternate = ["idZones"])
    val idZones: IdZonesResponse?,
    @SerializedName(value = "answer_zones", alternate = ["answerZones"])
    val answerZones: List<AnswerZoneResponse> = emptyList()
)
