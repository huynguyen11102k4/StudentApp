package com.examhub.student.model.request.auth

import com.google.gson.annotations.SerializedName

data class OtpRequest(
    val email: String,
    @SerializedName("type")
    val purpose: String = "register"
)
