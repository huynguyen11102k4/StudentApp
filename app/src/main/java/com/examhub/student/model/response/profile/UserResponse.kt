package com.examhub.student.model.response.profile

import com.google.gson.annotations.SerializedName

data class UserResponse(
    val id: String,
    val email: String,
    @SerializedName(value = "fullName", alternate = ["full_name", "name"])
    val fullName: String,
    val role: String,
    @SerializedName(value = "avatarUrl", alternate = ["avatar_url"])
    val avatarUrl: String? = null,
    @SerializedName(value = "isActive", alternate = ["is_active"])
    val isActive: Boolean? = null,
    @SerializedName(value = "googleId", alternate = ["google_id"])
    val googleId: String? = null,
    @SerializedName(value = "googleLinked", alternate = ["google_linked"])
    val googleLinked: Boolean? = null,
    @SerializedName(value = "mustChangePassword", alternate = ["must_change_password"])
    val mustChangePassword: Boolean? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at"])
    val updatedAt: String? = null,
    val teacher: TeacherProfileResponse? = null,
    val student: StudentProfileResponse? = null
)
