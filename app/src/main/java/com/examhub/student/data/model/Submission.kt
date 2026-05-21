package com.examhub.student.data.model

import java.io.Serializable

data class Submission(
    val id: String,
    val studentName: String,
    val studentId: String,
    val submittedAt: String,
    val status: String
) : Serializable
