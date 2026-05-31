package com.examhub.student.model.response.result

import com.google.gson.annotations.SerializedName

data class StudentResultDetailResponse(
    val id: String? = null,
    @SerializedName(value = "result_status", alternate = ["resultStatus", "status"])
    val resultStatus: String? = null,
    @SerializedName(value = "total_score", alternate = ["totalScore"])
    val totalScore: Double? = null,
    val source: String? = null,
    @SerializedName(value = "graded_at", alternate = ["gradedAt"])
    val gradedAt: String? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    val exam: StudentResultExamResponse? = null,
    val student: StudentResultStudentResponse? = null,
    @SerializedName(value = "student_name", alternate = ["studentName", "full_name", "fullName"])
    val studentName: String? = null,
    @SerializedName(value = "student_code", alternate = ["studentCode"])
    val studentCode: String? = null
) {
    fun displayStudentName(): String? {
        return listOf(student?.fullName, student?.name, studentName)
            .firstOrNull { !it.isNullOrBlank() }
    }

    fun displayStudentCode(): String? {
        return listOf(student?.studentCode, student?.code, studentCode)
            .firstOrNull { !it.isNullOrBlank() }
    }
}

data class StudentResultStudentResponse(
    val id: String? = null,
    @SerializedName(value = "full_name", alternate = ["fullName"])
    val fullName: String? = null,
    val name: String? = null,
    @SerializedName(value = "student_code", alternate = ["studentCode"])
    val studentCode: String? = null,
    val code: String? = null
)
