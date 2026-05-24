package com.examhub.student.model.response.appeal

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
