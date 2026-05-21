package com.examhub.student.model.response

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

data class NotificationResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("teacher_id")
    val teacherId: String?,

    @SerializedName("type")
    val type: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("content")
    val content: String,

    @SerializedName("link")
    val link: String?,

    @SerializedName("appeal_id")
    val appealId: String? = null,

    @SerializedName("target_id")
    val targetId: String? = null,

    @SerializedName("entity_id")
    val entityId: String? = null,

    @SerializedName("metadata")
    val metadata: JsonObject? = null,

    @SerializedName("data")
    val data: JsonObject? = null,

    @SerializedName("is_read")
    val isRead: Boolean,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String?
)

data class NotificationReadResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("is_read")
    val isRead: Boolean
)

data class NotificationUnreadCountResponse(
    @SerializedName("count")
    val count: Int
)

data class NotificationMarkAllReadResponse(
    @SerializedName("updated")
    val updated: Int
)
