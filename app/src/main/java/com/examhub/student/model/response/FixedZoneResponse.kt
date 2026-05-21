package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class FixedZoneResponse(
    @SerializedName(value = "zone_id", alternate = ["zoneId"])
    val zoneId: String,
    val type: String,
    val label: String,
    @SerializedName(value = "bounding_box", alternate = ["boundingBox"])
    val boundingBox: BoundingBoxResponse
)
