package com.examhub.student.model.response.classroom

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileClassInfoResponse(
    val id: String = "",
    @SerializedName("class_name")
    val className: String = "",
    val subject: String? = null,
    val description: String? = null,
    val grade: String? = null,
    @SerializedName("school_year")
    val schoolYear: String? = null,
    val status: String? = null,
    @SerializedName("class_code")
    val classCode: String? = null,
    @SerializedName("join_code")
    val joinCode: String? = null,
    @SerializedName("approval_mode")
    val approvalMode: String? = null,
    @SerializedName("student_count")
    val studentCount: Int? = null,
    val teacher: MobileClassTeacherResponse? = null,
    @SerializedName("_count")
    val count: MobileClassCountResponse? = null,
    @SerializedName("exam_count")
    val examCount: Int? = null
)
