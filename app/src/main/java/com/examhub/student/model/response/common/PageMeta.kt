package com.examhub.student.model.response.common

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PageMeta(
    val page: Int,
    val limit: Int,
    val total: Int,
    // Present in notification list response (section 9.1)
    @SerializedName(value = "unread_count", alternate = ["unreadCount"])
    val unreadCount: Int? = null
) : Serializable
