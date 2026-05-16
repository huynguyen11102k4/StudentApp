package com.omr.scanner.student.data.model

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
    val date: String = ""
) : Serializable
