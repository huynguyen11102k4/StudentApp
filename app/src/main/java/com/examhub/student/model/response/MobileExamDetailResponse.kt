package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class MobileExamDetailResponse(
    val id: String,
    val name: String,
    val subject: String,
    @SerializedName(value = "total_questions", alternate = ["totalQuestions"])
    val totalQuestions: Int,
    @SerializedName(value = "duration_minutes", alternate = ["duration"])
    val durationMinutes: Int,
    val status: String? = null,
    @SerializedName(value = "exam_type", alternate = ["examType"])
    val examType: String? = null,
    @SerializedName(value = "grading_type", alternate = ["gradingType"])
    val gradingType: String? = null,
    @SerializedName(value = "shuffle_mode", alternate = ["shuffleMode"])
    val shuffleMode: String? = null,
    @SerializedName("online_config")
    val onlineConfig: ExamOnlineConfigResponse? = null,
    @SerializedName("class")
    val classInfo: ExamClassInfoResponse? = null,
    @SerializedName("attempts_used")
    val attemptsUsed: Int? = null,
    @SerializedName("version_count")
    val versionCount: Int? = null,
    val template: ExamTemplateSummaryResponse? = null,
    @SerializedName("_count")
    val count: ExamCountResponse? = null
) : Serializable
