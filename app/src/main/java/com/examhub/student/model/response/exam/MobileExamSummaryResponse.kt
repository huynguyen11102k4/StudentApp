package com.examhub.student.model.response.exam

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
    @SerializedName(value = "start_time", alternate = ["startTime"])
    val startTime: String? = null,
    @SerializedName(value = "end_time", alternate = ["endTime"])
    val endTime: String? = null,
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
    val resultId: String? = null,
    @SerializedName(value = "can_start_session", alternate = ["canStartSession"])
    val canStartSession: Boolean? = null,
    @SerializedName(value = "can_submit", alternate = ["canSubmit"])
    val canSubmit: Boolean? = null,
    @SerializedName(value = "can_view_result", alternate = ["canViewResult"])
    val canViewResult: Boolean? = null,
    @SerializedName(value = "result_only", alternate = ["resultOnly"])
    val resultOnly: Boolean? = null
) {
    val displayTime: String
        get() = startTime ?: onlineConfig?.startTime ?: endTime ?: onlineConfig?.endTime.orEmpty()
}
