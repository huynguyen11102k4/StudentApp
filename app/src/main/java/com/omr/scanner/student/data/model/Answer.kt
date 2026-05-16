package com.omr.scanner.student.data.model

data class Answer(
    val questionNo: Int,
    val studentAnswer: String?,
    val correctAnswer: String,
    val status: String
)
