package com.omr.scanner.student.model.request

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
    val deviceId: String? = null
)
