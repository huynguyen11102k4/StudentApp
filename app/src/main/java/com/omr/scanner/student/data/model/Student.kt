package com.omr.scanner.student.data.model

import java.io.Serializable

data class Student(
    val id: String,
    val name: String,
    val studentId: String,
    val hsCode: String = ""
) : Serializable
