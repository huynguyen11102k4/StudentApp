package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileClassResponse(
    val id: String,
    @SerializedName("classId")
    val classId: String = "",
    @SerializedName("internalId")
    val internalId: String? = null,
    @SerializedName("class_name")
    val className: String = "",
    @SerializedName("class")
    val classInfo: MobileClassInfoResponse? = null,
    val subject: String? = null,
    val description: String? = null,
    @SerializedName("join_code")
    val joinCode: String? = null,
    @SerializedName("approval_mode")
    val approvalMode: String? = null,
    val grade: String,
    @SerializedName("school_year")
    val schoolYear: String,
    val status: String? = null,
    @SerializedName("student_count")
    val studentCount: Int,
    @SerializedName("exam_count")
    val examCount: Int? = null,
    @SerializedName("_count")
    val count: MobileClassCountResponse? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
) : Serializable

data class MobileClassInfoResponse(
    val id: String,
    val className: String,
    val subject: String? = null,
    val description: String? = null,
    val grade: String? = null,
    val schoolYear: String? = null,
    val status: String? = null
) : Serializable

data class MobileClassCountResponse(
    val exams: Int? = null,
    val examAssignments: Int? = null,
    val classMembers: Int? = null
) : Serializable
