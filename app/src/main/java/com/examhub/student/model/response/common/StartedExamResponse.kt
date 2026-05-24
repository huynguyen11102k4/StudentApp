package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StartedExamResponse(
    val id: String,
    val name: String,
    val subject: String,
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    @SerializedName("total_questions")
    val totalQuestions: Int,
    @SerializedName("exam_type")
    val examType: String?,
    @SerializedName("grading_type")
    val gradingType: String?,
    @SerializedName(value = "student_code_type", alternate = ["studentCodeType", "student_id_type", "studentIdType", "student_identifier_type", "studentIdentifierType", "identification_mode", "identificationMode", "identifier_type", "identifierType", "code_type", "codeType"])
    val studentCodeType: String? = null,
    val status: String?
)
