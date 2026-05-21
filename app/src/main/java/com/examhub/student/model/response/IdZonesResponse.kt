package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class IdZonesResponse(
    @SerializedName(value = "max_digits", alternate = ["maxDigits"])
    val maxDigits: Int,
    @SerializedName(value = "digit_config", alternate = ["digitConfig"])
    val digitConfig: DigitConfigResponse,
    @SerializedName(value = "bounding_box", alternate = ["boundingBox"])
    val boundingBox: BoundingBoxResponse
)
