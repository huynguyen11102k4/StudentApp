package com.omr.scanner.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileExamSummaryResponse(
    val id: String,
    val name: String,
    val subject: String,
    @SerializedName("total_questions")
    val totalQuestions: Int,
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    val status: String,
    @SerializedName("exam_type")
    val examType: String? = null,
    @SerializedName("grading_type")
    val gradingType: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null,
    val template: ExamTemplateSummaryResponse? = null,
    @SerializedName("_count")
    val count: ExamCountResponse? = null
) : Serializable
