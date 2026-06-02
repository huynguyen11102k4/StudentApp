package com.examhub.student.data.model

import java.io.Serializable

data class SchoolClass(
    val id: String,
    val name: String,
    val subject: String,
    val classCode: String = "",
    val joinCode: String,
    val studentCount: Int,
    val status: String? = null,
    val hasOfflineData: Boolean = false
) : Serializable
