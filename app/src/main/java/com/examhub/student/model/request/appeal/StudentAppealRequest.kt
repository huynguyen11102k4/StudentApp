package com.examhub.student.model.request.appeal

import com.google.gson.annotations.SerializedName

data class StudentAppealRequest(
    @SerializedName("sheet_id")
    val sheetId: String,
    val reason: String,
    val items: List<StudentAppealItemRequest>
)
