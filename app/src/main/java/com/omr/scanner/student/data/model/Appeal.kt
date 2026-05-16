package com.omr.scanner.student.data.model

data class Appeal(
    val id: String,
    val studentId: String = "",
    val studentName: String,
    val studentCode: String,
    val examId: String = "",
    val examName: String,
    val subject: String,
    val sheetId: String = "",
    val oldScore: Double,
    val newScore: Double? = null,
    val reason: String,
    val status: String,
    val createdAt: String,
    val teacherNote: String? = null,
    val processedImageUrl: String? = null,
    val dewarpedImageUrl: String? = null,
    val itemMessages: String = ""
)
