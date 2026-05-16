package com.omr.scanner.student.service

import com.omr.scanner.student.model.response.ApiEnvelope
import com.omr.scanner.student.model.request.FcmTokenRequest
import com.omr.scanner.student.model.response.FcmTokenResponse
import com.omr.scanner.student.model.response.NotificationMarkAllReadResponse
import com.omr.scanner.student.model.response.NotificationReadResponse
import com.omr.scanner.student.model.response.NotificationResponse
import com.omr.scanner.student.model.response.NotificationUnreadCountResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApiService {
    @GET("student/notifications")
    suspend fun getNotifications(
        @Query("page") page: String = "1",
        @Query("limit") limit: String = "20",
        @Query("unreadOnly") unreadOnly: Boolean? = null
    ): Response<PagedEnvelope<NotificationResponse>>

    @PATCH("student/notifications/{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String): Response<ApiEnvelope<NotificationReadResponse>>

    @POST("student/notifications/{notificationId}/read")
    suspend fun markAsReadLegacy(@Path("notificationId") notificationId: String): Response<ApiEnvelope<NotificationReadResponse>>

    @GET("student/notifications/unread-count")
    suspend fun getUnreadCount(): Response<ApiEnvelope<NotificationUnreadCountResponse>>

    @POST("student/notifications/mark-all-read")
    suspend fun markAllAsRead(): Response<ApiEnvelope<NotificationMarkAllReadResponse>>

    @POST("mobile/notifications/fcm-token")
    suspend fun registerFcmToken(@retrofit2.http.Body request: FcmTokenRequest): Response<FcmTokenResponse>
}
