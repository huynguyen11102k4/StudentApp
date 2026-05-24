package com.examhub.student.model.response.notification

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

data class NotificationMarkAllReadResponse(
    @SerializedName("updated")
    val updated: Int
)
