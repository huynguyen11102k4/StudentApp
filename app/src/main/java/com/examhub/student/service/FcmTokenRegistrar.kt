package com.examhub.student.service

import android.content.Context
import android.util.Log
import com.examhub.student.model.request.FcmTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmTokenRegistrar(
    private val notificationApiService: NotificationApiService,
    private val tokenManager: TokenManager,
    private val notificationPreferenceManager: NotificationPreferenceManager,
    private val appContext: Context? = null
) {
    /**
     * Sync the current Firebase token with the backend.
     * Should be called after login/register success and on app startup.
     * API: POST /student/notifications/fcm-token (with X-Device-Id header)
     */
    fun syncCurrentToken(scope: CoroutineScope) {
        if (tokenManager.getAccessToken() == null) return // not logged in yet
        val cachedToken = tokenManager.getFcmToken()
        if (!cachedToken.isNullOrBlank()) {
            registerToken(cachedToken, scope)
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) registerToken(token, scope)
            }
            .addOnFailureListener { e ->
                Log.w("FcmTokenRegistrar", "Failed to fetch FCM token: ${e.message}")
            }
    }

    /**
     * Register a new Firebase token received from onNewToken callback.
     */
    fun registerToken(token: String, scope: CoroutineScope) {
        if (tokenManager.getAccessToken() == null) {
            // Not logged in; save token locally — will be synced after login via syncCurrentToken()
            tokenManager.saveFcmToken(token)
            return
        }
        tokenManager.saveFcmToken(token)
        scope.launch(Dispatchers.IO) {
            runCatching {
                val request = FcmTokenRequest(
                    fcmToken = token,
                    appVersion = getAppVersion()
                )
                notificationApiService.updateFcmToken(request)
            }.onFailure { e ->
                Log.w("FcmTokenRegistrar", "Failed to sync FCM token: ${e.message}")
            }
        }
    }

    private fun getAppVersion(): String? = runCatching {
        appContext?.packageManager
            ?.getPackageInfo(appContext.packageName, 0)
            ?.versionName
    }.getOrNull()
}
