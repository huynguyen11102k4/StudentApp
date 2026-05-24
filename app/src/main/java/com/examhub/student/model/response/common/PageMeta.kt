package com.examhub.student.model.response.common

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class PageMeta(
    val page: Int,
    val limit: Int,
    val total: Int,
    // Present in notification list response (section 9.1)
    @SerializedName("unread_count")
    val unreadCount: Int? = null
) : Serializable
