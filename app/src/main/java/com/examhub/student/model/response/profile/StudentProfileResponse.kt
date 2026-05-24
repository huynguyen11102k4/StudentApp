package com.examhub.student.model.response.profile

import com.google.gson.annotations.SerializedName

data class StudentProfileResponse(
    val id: String? = null,
    @SerializedName(value = "internalId", alternate = ["internal_id"])
    val internalId: String? = null,
    @SerializedName(value = "studentCode", alternate = ["student_code", "code"])
    val studentCode: String? = null,
    val dateOfBirth: String? = null
)
