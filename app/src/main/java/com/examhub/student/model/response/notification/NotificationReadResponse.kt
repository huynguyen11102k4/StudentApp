package com.examhub.student.model.response.notification

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

data class NotificationReadResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("is_read")
    val isRead: Boolean
)
