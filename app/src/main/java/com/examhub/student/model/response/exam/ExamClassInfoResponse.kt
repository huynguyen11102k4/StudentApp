package com.examhub.student.model.response.exam

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import java.io.Serializable

data class ExamClassInfoResponse(
    val id: String? = null,
    @SerializedName("class_name")
    val className: String? = null,
    @SerializedName("class_code")
    val classCode: String? = null
)
