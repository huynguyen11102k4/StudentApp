package com.omr.scanner.student.service

import com.google.firebase.messaging.FirebaseMessaging
import com.omr.scanner.student.BuildConfig
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FcmTokenRegistrar(
    private val notificationRepository: NotificationRepository,
    private val tokenManager: TokenManager,
    private val notificationPreferenceManager: NotificationPreferenceManager
) {
    fun syncCurrentToken(scope: CoroutineScope) {
        if (!tokenManager.hasToken() || !notificationPreferenceManager.isEnabled()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            registerToken(token, scope)
        }
    }

    fun registerToken(token: String, scope: CoroutineScope) {
        if (!tokenManager.hasToken() || !notificationPreferenceManager.isEnabled() || token.isBlank()) return
        scope.launch(Dispatchers.IO) {
            notificationRepository.registerFcmToken(token, BuildConfig.VERSION_NAME).collect { result ->
                if (result is ApiResult.Success) {
                    tokenManager.saveFcmToken(token)
                }
            }
        }
    }
}
