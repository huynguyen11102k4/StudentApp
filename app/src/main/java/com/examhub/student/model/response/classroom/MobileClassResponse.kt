package com.examhub.student.model.response.classroom

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileClassResponse(
    val id: String,
    @SerializedName("student_id")
    val studentId: String? = null,
    @SerializedName("class_id")
    val classId: String = "",
    @SerializedName("internal_id")
    val internalId: String? = null,
    val status: String? = null,
    @SerializedName("joined_at")
    val joinedAt: String? = null,
    @SerializedName("approved_at")
    val approvedAt: String? = null,
    @SerializedName("student_count")
    val studentCount: Int? = null,
    @SerializedName("class")
    val classInfo: MobileClassInfoResponse? = null,
    @SerializedName("_count")
    val count: MobileClassCountResponse? = null,
    @SerializedName("exam_count")
    val examCount: Int? = null
)
