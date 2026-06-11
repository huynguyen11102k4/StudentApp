package com.examhub.student.model.response.auth

import com.google.gson.annotations.SerializedName

data class GoogleLinkResponse(
    @SerializedName(value = "googleLinked", alternate = ["google_linked"])
    val googleLinked: Boolean,
    @SerializedName(value = "googleId", alternate = ["google_id"])
    val googleId: String? = null,
    val updated: Boolean? = null
)
