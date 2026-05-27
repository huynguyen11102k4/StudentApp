package com.examhub.student.model.response.submission

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StudentSubmitResponse(
    @SerializedName("submission_id")
    val submissionId: String,
    @SerializedName(value = "result_id", alternate = ["resultId", "answer_sheet_id", "answerSheetId", "sheet_id", "sheetId"])
    val resultId: String? = null,
    val status: String,
    @SerializedName("submitted_at")
    val submittedAt: String,
    @SerializedName("session_status")
    val sessionStatus: String
)
