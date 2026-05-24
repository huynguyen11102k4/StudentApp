package com.examhub.student.model.response.profile

import com.google.gson.annotations.SerializedName

data class TeacherProfileResponse(
    val id: String? = null,
    val department: String? = null,
    val specialization: String? = null
)
