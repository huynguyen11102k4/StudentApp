package com.examhub.student.model.response.submission

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StudentSubmitResponse(
    @SerializedName(value = "submission_id", alternate = ["submissionId", "id"])
    val submissionId: String = "",
    @SerializedName(value = "result_id", alternate = ["resultId", "answer_sheet_id", "answerSheetId", "sheet_id", "sheetId"])
    val resultId: String? = null,
    @SerializedName(value = "status", alternate = ["submission_status", "submissionStatus"])
    val status: String = "",
    @SerializedName(value = "submitted_at", alternate = ["submittedAt"])
    val submittedAt: String = "",
    @SerializedName(value = "session_status", alternate = ["sessionStatus"])
    val sessionStatus: String = "",
    val duplicate: Boolean = false
)
