package com.examhub.student.model.request.auth

import com.google.gson.annotations.SerializedName

data class StudentRegisterRequest(
    val email: String,
    @SerializedName("full_name")
    val fullName: String,
    val password: String? = null,
    @SerializedName("google_id_token")
    val googleIdToken: String? = null,
    @SerializedName("student_code")
    val studentCode: String? = null,
    @SerializedName("device_id")
    val deviceId: String? = null,
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null
)
