package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName

data class AppealSummaryResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("reason")
    val reason: String?,

    @SerializedName("teacher_note")
    val teacherNote: String?,

    @SerializedName(value = "old_score", alternate = ["oldScore"])
    val oldScore: Double?,

    @SerializedName(value = "new_score", alternate = ["newScore"])
    val newScore: Double?,

    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String,

    @SerializedName(value = "updated_at", alternate = ["updatedAt"])
    val updatedAt: String? = null,

    @SerializedName("student")
    val student: AppealStudentResponse?,

    @SerializedName("exam")
    val exam: AppealExamResponse?,

    @SerializedName("sheet")
    val sheet: AppealSheetResponse?,

    @SerializedName("items")
    val items: List<AppealItemResponse>? = null
)

data class AppealStudentResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("code")
    val code: String
)

data class AppealExamResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("subject")
    val subject: String?
)

data class AppealSheetResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("total_score")
    val totalScore: Double?,

    @SerializedName("processed_image_url")
    val processedImageUrl: String?,

    @SerializedName("dewarped_image_url")
    val dewarpedImageUrl: String?
)

data class AppealItemResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("question_number")
    val questionNumber: Int,

    @SerializedName("student_message")
    val studentMessage: String?,

    @SerializedName("teacher_response")
    val teacherResponse: String?,

    @SerializedName("status")
    val status: String
)

data class AppealResolveResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("teacher_note")
    val teacherNote: String?,

    @SerializedName("items")
    val items: List<AppealResolvedItemResponse>? = null
)

data class AppealResolvedItemResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("teacher_response")
    val teacherResponse: String?
)
