package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import java.io.Serializable

data class MobileExamSummaryResponse(
    // Backend returns "exam_id" as the primary key in upcoming list
    @SerializedName(value = "exam_id", alternate = ["id"])
    val id: String,
    val name: String,
    val subject: String,
    @SerializedName("total_questions")
    val totalQuestions: Int,
    @SerializedName("duration_minutes")
    val durationMinutes: Int,
    @SerializedName("exam_type")
    val examType: String? = null,
    @SerializedName("grading_type")
    val gradingType: String? = null,
    val status: String? = null,
    @SerializedName("online_config")
    val onlineConfig: ExamOnlineConfigResponse? = null,
    @SerializedName("class")
    val classInfo: ExamClassInfoResponse? = null,
    val template: ExamTemplateSummaryResponse? = null,
    @SerializedName("_count")
    val count: ExamCountResponse? = null,
    @SerializedName(value = "attempts_used", alternate = ["attemptsUsed"])
    val attemptsUsed: Int? = null,
    @SerializedName(value = "submission_status", alternate = ["submissionStatus"])
    val submissionStatus: String? = null,
    @SerializedName(value = "result_id", alternate = ["resultId", "sheet_id", "sheetId", "answer_sheet_id", "answerSheetId"])
    val resultId: String? = null
) : Serializable

data class ExamOnlineConfigResponse(
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null,
    @SerializedName("max_attempts")
    val maxAttempts: Int? = null,
    @SerializedName("is_locked_mode")
    val isLockedMode: Boolean? = null,
    @SerializedName("lock_config")
    val lockConfig: JsonElement? = null
) : Serializable

data class ExamClassInfoResponse(
    val id: String? = null,
    @SerializedName("class_name")
    val className: String? = null,
    @SerializedName("class_code")
    val classCode: String? = null
) : Serializable
