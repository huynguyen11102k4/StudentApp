package com.examhub.student.data.model

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val link: String?,
    val appealId: String?,
    val isRead: Boolean,
    val createdAt: String
)
