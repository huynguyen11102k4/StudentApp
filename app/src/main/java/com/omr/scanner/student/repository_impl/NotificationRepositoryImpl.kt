package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.FcmTokenRequest
import com.omr.scanner.student.model.response.FcmTokenResponse
import com.omr.scanner.student.model.response.NotificationMarkAllReadResponse
import com.omr.scanner.student.model.response.NotificationReadResponse
import com.omr.scanner.student.model.response.NotificationResponse
import com.omr.scanner.student.model.response.NotificationUnreadCountResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.repository.NotificationRepository
import com.omr.scanner.student.service.NotificationApiService
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

    override fun registerFcmToken(fcmToken: String, appVersion: String?): Flow<ApiResult<FcmTokenResponse>> =
        safeApiFlow(gson) { apiService.registerFcmToken(FcmTokenRequest(fcmToken, appVersion)) }
}
