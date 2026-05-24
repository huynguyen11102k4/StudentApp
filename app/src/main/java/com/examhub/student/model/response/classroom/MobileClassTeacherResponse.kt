package com.examhub.student.model.response.classroom

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileClassTeacherResponse(
    val id: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    val email: String? = null
)
