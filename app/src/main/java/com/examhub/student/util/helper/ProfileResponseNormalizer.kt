package com.examhub.student.util.helper

import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.model.response.auth.AuthMethodsResponse
import com.google.gson.Gson
import com.google.gson.JsonObject

fun Gson.parseUserProfileJson(raw: String): UserResponse? {
    return runCatching {
        val root = fromJson(raw, JsonObject::class.java)
        val profileJson = root?.getAsJsonObject("data") ?: root
        fromJson(profileJson, UserResponse::class.java)?.sanitizedStudentProfile()
    }.getOrNull()
}

fun UserResponse.sanitizedStudentProfile(): UserResponse {
    val rawId: String? = id
    val rawEmail: String? = email
    val rawFullName: String? = fullName
    val rawRole: String? = role

    val linked = googleLinked ?: authMethods?.google ?: false
    val passwordAvailable = hasPassword ?: authMethods?.password
    return copy(
        id = rawId.orEmpty(),
        email = rawEmail.orEmpty(),
        fullName = rawFullName.orEmpty(),
        role = rawRole.orEmpty().ifBlank { "STUDENT" },
        avatarUrl = avatarUrl.orEmpty(),
        googleLinked = linked,
        hasPassword = passwordAvailable,
        authMethods = if (authMethods != null || hasPassword != null || googleLinked != null) {
            AuthMethodsResponse(
                password = passwordAvailable ?: false,
                google = linked
            )
        } else {
            null
        },
        student = student?.copy(
            id = student.id.orEmpty(),
            internalId = student.internalId.orEmpty(),
            studentCode = student.studentCode.orEmpty(),
            dateOfBirth = student.dateOfBirth?.substringBefore("T")?.trim()
        )
    )
}
