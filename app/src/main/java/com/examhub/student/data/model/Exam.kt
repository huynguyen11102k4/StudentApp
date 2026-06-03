package com.examhub.student.data.model

import java.io.Serializable

data class Exam(
    val id: String,
    val name: String,
    val subject: String,
    val className: String,
    val duration: Int,
    val questionCount: Int,
    val status: String,
    val gradedCount: Int,
    val totalStudents: Int,
    val isOfflineReady: Boolean = false,
    val date: String = "",
    val resultSheetId: String? = null,
    val hasSubmitted: Boolean = false,
    val gradingType: String = "",
    val canStartSession: Boolean = false,
    val canSubmit: Boolean = false,
    val canViewResult: Boolean = false,
    val resultOnly: Boolean = false
) : Serializable
