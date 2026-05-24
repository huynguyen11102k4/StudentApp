package com.examhub.student.service


import com.examhub.student.model.request.notification.FcmTokenRequest
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.notification.FcmTokenResponse
import com.examhub.student.model.response.notification.NotificationMarkAllReadResponse
import com.examhub.student.model.response.notification.NotificationReadResponse
import com.examhub.student.model.response.notification.NotificationResponse
import com.examhub.student.model.response.notification.NotificationUnreadCountResponse
import com.examhub.student.model.response.common.PagedEnvelope
import retrofit2.Response
import retrofit2.http.Body
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

    // API doc: POST /student/notifications/:id/read
    @POST("student/notifications/{notificationId}/read")
    suspend fun markAsRead(@Path("notificationId") notificationId: String): Response<ApiEnvelope<NotificationReadResponse>>

    // PATCH kept for backward-compat
    @PATCH("student/notifications/{notificationId}/read")
    suspend fun markAsReadLegacy(@Path("notificationId") notificationId: String): Response<ApiEnvelope<NotificationReadResponse>>

    @GET("student/notifications/unread-count")
    suspend fun getUnreadCount(): Response<ApiEnvelope<NotificationUnreadCountResponse>>

    @POST("student/notifications/mark-all-read")
    suspend fun markAllAsRead(): Response<ApiEnvelope<NotificationMarkAllReadResponse>>

    // API doc 9.3: POST /student/notifications/fcm-token
    @POST("student/notifications/fcm-token")
    suspend fun updateFcmToken(@Body request: FcmTokenRequest): Response<ApiEnvelope<FcmTokenResponse>>
}
