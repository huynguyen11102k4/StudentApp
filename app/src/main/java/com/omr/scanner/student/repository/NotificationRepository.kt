package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.FcmTokenResponse
import com.omr.scanner.student.model.response.NotificationMarkAllReadResponse
import com.omr.scanner.student.model.response.NotificationReadResponse
import com.omr.scanner.student.model.response.NotificationResponse
import com.omr.scanner.student.model.response.NotificationUnreadCountResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(
        page: String = "1",
        limit: String = "20",
        unreadOnly: Boolean? = null
    ): Flow<ApiResult<PagedEnvelope<NotificationResponse>>>

    fun markAsRead(notificationId: String): Flow<ApiResult<NotificationReadResponse>>

    fun getUnreadCount(): Flow<ApiResult<NotificationUnreadCountResponse>>

    fun markAllAsRead(): Flow<ApiResult<NotificationMarkAllReadResponse>>

    fun registerFcmToken(fcmToken: String, appVersion: String? = null): Flow<ApiResult<FcmTokenResponse>>
}
