package com.examhub.student.model.request.profile


import com.google.gson.annotations.SerializedName

data class UpdateProfileRequest(
    @SerializedName(value = "full_name", alternate = ["fullName"])
    val fullName: String,
    @SerializedName(value = "date_of_birth", alternate = ["dateOfBirth"])
    val dateOfBirth: String? = null
)
