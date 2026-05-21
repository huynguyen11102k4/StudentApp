package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// Represents the class-member wrapper returned by GET /student/classes and GET /student/classes/:classId
data class MobileClassResponse(
    val id: String,                       // class-member UUID
    val studentId: String? = null,
    val classId: String = "",
    val internalId: String? = null,
    val status: String? = null,           // APPROVED | PENDING
    val joinedAt: String? = null,
    val approvedAt: String? = null,
    @SerializedName("class")
    val classInfo: MobileClassInfoResponse? = null
) : Serializable

data class MobileClassInfoResponse(
    val id: String = "",
    val className: String = "",
    val subject: String? = null,
    val description: String? = null,
    val grade: String? = null,
    val schoolYear: String? = null,
    val status: String? = null,
    val teacher: MobileClassTeacherResponse? = null,
    @SerializedName("_count")
    val count: MobileClassCountResponse? = null,
    // legacy flat fields (older backend versions)
    @SerializedName("join_code")
    val joinCode: String? = null,
    @SerializedName("approval_mode")
    val approvalMode: String? = null
) : Serializable

data class MobileClassTeacherResponse(
    val user: MobileClassTeacherUserResponse? = null
) : Serializable

data class MobileClassTeacherUserResponse(
    val fullName: String? = null,
    val email: String? = null
) : Serializable

data class MobileClassCountResponse(
    val exams: Int? = null,
    val examAssignments: Int? = null,
    val classMembers: Int? = null,
    val students: Int? = null,
    val members: Int? = null
) : Serializable
