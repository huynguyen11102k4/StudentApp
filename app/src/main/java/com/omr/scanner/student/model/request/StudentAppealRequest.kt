package com.omr.scanner.student.model.request

import com.google.gson.annotations.SerializedName

data class StudentAppealRequest(
    @SerializedName("sheet_id")
    val sheetId: String,
    val reason: String,
    val items: List<StudentAppealItemRequest>
)

data class StudentAppealItemRequest(
    @SerializedName("question_number")
    val questionNumber: Int,
    @SerializedName("student_message")
    val studentMessage: String
)
