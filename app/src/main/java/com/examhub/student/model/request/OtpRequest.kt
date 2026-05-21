package com.examhub.student.model.request

import com.google.gson.annotations.SerializedName

data class OtpRequest(
    val email: String,
    @SerializedName("type")
    val purpose: String = "register"
)
