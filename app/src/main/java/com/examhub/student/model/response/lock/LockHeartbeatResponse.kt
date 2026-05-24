package com.examhub.student.model.response.lock

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class LockHeartbeatResponse(
    val status: String,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int
)
