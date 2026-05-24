package com.examhub.student.model.response.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class StartedOnlineConfigResponse(
    @SerializedName("is_locked_mode")
    val isLockedMode: Boolean = false,
    @SerializedName("lock_config")
    val lockConfig: JsonElement? = null
)
