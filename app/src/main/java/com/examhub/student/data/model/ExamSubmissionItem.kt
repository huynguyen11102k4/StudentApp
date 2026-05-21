package com.examhub.student.data.model

import java.io.Serializable

data class ExamSubmissionItem(
    val id: String,
    val imageUrl: String?,
    val studentName: String,
    val studentCode: String,
    val status: String,
    val score: Double?,
    val submittedAt: String
) : Serializable
