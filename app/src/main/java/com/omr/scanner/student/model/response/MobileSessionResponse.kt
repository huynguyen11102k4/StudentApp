package com.omr.scanner.student.model.response

data class MobileSessionResponse(
    val id: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val fcmToken: String? = null,
    val appVersion: String? = null,
    val lastActive: String? = null,
    val createdAt: String? = null,
    val isCurrent: Boolean = false
)
