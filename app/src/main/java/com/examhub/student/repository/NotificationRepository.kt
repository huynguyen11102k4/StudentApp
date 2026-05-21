package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.NotificationMarkAllReadResponse
import com.examhub.student.model.response.NotificationReadResponse
import com.examhub.student.model.response.NotificationResponse
import com.examhub.student.model.response.NotificationUnreadCountResponse
import com.examhub.student.model.response.PagedEnvelope
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
}
