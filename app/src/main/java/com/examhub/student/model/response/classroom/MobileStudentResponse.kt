package com.examhub.student.model.response.classroom

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileStudentResponse(
    @SerializedName("student_id")
    val studentId: String? = null,
    @SerializedName("internal_id")
    val internalId: String? = null,
    @SerializedName("student_code")
    val studentCode: String,
    @SerializedName("full_name")
    val fullName: String,
    val email: String?
) : Serializable
