package com.examhub.student.data.model

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val link: String?,
    val appealId: String?,
    val targetId: String? = null,
    val entityId: String? = null,
    val metadata: com.google.gson.JsonObject? = null,
    val data: com.google.gson.JsonObject? = null,
    val isRead: Boolean,
    val createdAt: String
)
