package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.notification.NotificationMarkAllReadResponse
import com.examhub.student.model.response.notification.NotificationReadResponse
import com.examhub.student.model.response.notification.NotificationResponse
import com.examhub.student.model.response.common.PagedEnvelope
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getNotifications(
        page: String = "1",
        limit: String = "20",
        unreadOnly: Boolean? = null
    ): Flow<ApiResult<PagedEnvelope<NotificationResponse>>>

    fun markAsRead(notificationId: String): Flow<ApiResult<NotificationReadResponse>>

    fun markAllAsRead(): Flow<ApiResult<NotificationMarkAllReadResponse>>
}
