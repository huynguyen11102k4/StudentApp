package com.examhub.student.model.response.lock

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class LockValidateSessionResponse(
    val valid: Boolean,
    val status: String? = null,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int? = null,
    @SerializedName("lock_config")
    val lockConfig: JsonElement? = null,
    @SerializedName("is_locked_mode")
    val isLockedMode: Boolean? = null,
    val reason: String? = null
)
