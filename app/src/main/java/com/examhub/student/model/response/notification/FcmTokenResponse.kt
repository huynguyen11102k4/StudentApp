package com.examhub.student.model.response.notification


// Response for POST /student/notifications/fcm-token
// Returns the updated MobileSession object
data class FcmTokenResponse(
    val id: String? = null,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val lastActive: String? = null,
    val updatedAt: String? = null
)
