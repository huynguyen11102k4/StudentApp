package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StartedSessionResponse(
    @SerializedName("session_id")
    val sessionId: String,
    @SerializedName("attempt_no")
    val attemptNo: Int,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int
)
