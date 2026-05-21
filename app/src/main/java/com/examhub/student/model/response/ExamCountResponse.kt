package com.examhub.student.model.response

import java.io.Serializable

data class ExamCountResponse(
    val examVersions: Int = 0,
    val answerSheets: Int = 0,
    val examAssignments: Int = 0,
    val examQuestions: Int = 0
) : Serializable
