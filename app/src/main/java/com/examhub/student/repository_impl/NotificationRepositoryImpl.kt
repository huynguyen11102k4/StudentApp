package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.NotificationMarkAllReadResponse
import com.examhub.student.model.response.NotificationReadResponse
import com.examhub.student.model.response.NotificationResponse
import com.examhub.student.model.response.NotificationUnreadCountResponse
import com.examhub.student.model.response.PagedEnvelope
import com.examhub.student.repository.NotificationRepository
import com.examhub.student.service.NotificationApiService
import kotlinx.coroutines.flow.Flow

class NotificationRepositoryImpl(
    private val apiService: NotificationApiService,
    private val gson: Gson
) : NotificationRepository {
    override fun getNotifications(
        page: String,
        limit: String,
        unreadOnly: Boolean?
    ): Flow<ApiResult<PagedEnvelope<NotificationResponse>>> =
        safeApiFlow(gson) { apiService.getNotifications(page, limit, unreadOnly) }

    override fun markAsRead(notificationId: String): Flow<ApiResult<NotificationReadResponse>> =
        safeEnvelopeFlow(gson) { apiService.markAsRead(notificationId) }

    override fun getUnreadCount(): Flow<ApiResult<NotificationUnreadCountResponse>> =
        safeEnvelopeFlow(gson) { apiService.getUnreadCount() }

    override fun markAllAsRead(): Flow<ApiResult<NotificationMarkAllReadResponse>> =
        safeEnvelopeFlow(gson) { apiService.markAllAsRead() }
}
