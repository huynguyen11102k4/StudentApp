package com.examhub.student.model.request.submission

import com.google.gson.annotations.SerializedName

data class IdZoneResultRequest(
    @SerializedName("student_id")
    val studentId: String?,
    @SerializedName("class_code")
    val classCode: String?,
    @SerializedName("exam_code")
    val examCode: String?,
    @SerializedName("id_ok")
    val idOk: Boolean,
    @SerializedName("id_error")
    val idError: String? = null
)
