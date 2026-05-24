package com.examhub.student.model.response

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
) : Serializable

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
) : Serializable

data class MobileClassCountResponse(
    val students: Int? = null,
    val exams: Int? = null,
    @SerializedName("student_classes")
    val studentClasses: Int? = null,
    @SerializedName("studentClasses")
    val studentClassesCamel: Int? = null
) : Serializable

data class MobileClassTeacherResponse(
    val id: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    val email: String? = null
) : Serializable
