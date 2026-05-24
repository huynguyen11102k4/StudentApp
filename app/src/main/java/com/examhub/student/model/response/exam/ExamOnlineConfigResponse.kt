package com.examhub.student.model.response.exam

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonElement
import java.io.Serializable

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
)
