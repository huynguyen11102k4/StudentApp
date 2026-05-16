package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileExamDetailResponse(
    val id: String,
    val name: String,
    val subject: String,
    val totalQuestions: Int,
    val duration: Int,
    val status: String,
    val examType: String,
    val gradingType: String,
    val shuffleMode: String,
    val template: ExamTemplateSummaryResponse?,
    @SerializedName("_count")
    val count: ExamCountResponse?
) : Serializable
