package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class UserResponse(
    val id: String,
    val email: String,
    @SerializedName(value = "fullName", alternate = ["full_name", "name"])
    val fullName: String,
    val role: String,
    @SerializedName(value = "avatarUrl", alternate = ["avatar_url"])
    val avatarUrl: String? = null,
    val isActive: Boolean? = null,
    val googleId: String? = null,
    val mustChangePassword: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val teacher: TeacherProfileResponse? = null,
    val student: StudentProfileResponse? = null
)

data class TeacherProfileResponse(
    val id: String? = null,
    val department: String? = null,
    val specialization: String? = null
)

data class StudentProfileResponse(
    val id: String? = null,
    val studentCode: String? = null,
    val dateOfBirth: String? = null
)
