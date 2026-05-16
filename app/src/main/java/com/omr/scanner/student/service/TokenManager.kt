package com.omr.scanner.student.service

import android.content.Context
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.util.UUID

class TokenManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun saveTokens(accessToken: String, refreshToken: String) {
        val expiresAt = decodeJwtExpiry(accessToken)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getCachedAvatarUrl(): String? = prefs.getString(KEY_AVATAR_URL, null)

    fun saveCachedAvatarUrl(avatarUrl: String?) {
        val editor = prefs.edit()
        if (avatarUrl.isNullOrBlank()) {
            editor.remove(KEY_AVATAR_URL)
        } else {
            editor.putString(KEY_AVATAR_URL, avatarUrl)
        }
        editor.apply()
    }

    fun getCachedProfileJson(): String? = prefs.getString(KEY_PROFILE_JSON, null)

    fun saveCachedProfileJson(profileJson: String?) {
        val editor = prefs.edit()
        if (profileJson.isNullOrBlank()) {
            editor.remove(KEY_PROFILE_JSON)
        } else {
            editor.putString(KEY_PROFILE_JSON, profileJson)
        }
        editor.apply()
    }

    fun getCachedSessionsJson(): String? = prefs.getString(KEY_SESSIONS_JSON, null)

    fun saveCachedSessionsJson(sessionsJson: String?) {
        val editor = prefs.edit()
        if (sessionsJson.isNullOrBlank()) {
            editor.remove(KEY_SESSIONS_JSON)
        } else {
            editor.putString(KEY_SESSIONS_JSON, sessionsJson)
        }
        editor.apply()
    }

    fun saveFcmToken(token: String?) {
        val editor = prefs.edit()
        if (token.isNullOrBlank()) {
            editor.remove(KEY_FCM_TOKEN)
        } else {
            editor.putString(KEY_FCM_TOKEN, token)
        }
        editor.apply()
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM_TOKEN, null)

    fun clearTokens(notifySessionExpired: Boolean = false) {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_PROFILE_JSON)
            .remove(KEY_SESSIONS_JSON)
            .remove(KEY_FCM_TOKEN)
            .apply()
        if (notifySessionExpired) {
            _authEvents.tryEmit(AuthEvent.SessionExpired)
        }
    }

    fun hasToken(): Boolean = !getAccessToken().isNullOrBlank()

    fun isTokenValid(): Boolean {
        val expiresAt = getExpiresAt()
        return expiresAt > 0L && expiresAt > System.currentTimeMillis() / 1000
    }

    fun isTokenExpiringSoon(withinSeconds: Long = 300): Boolean {
        val expiresAt = getExpiresAt()
        return expiresAt > 0L && (expiresAt - System.currentTimeMillis() / 1000) <= withinSeconds
    }

    fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        val generated = androidId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    /** Decode JWT without 3rd-party library. Returns expiry in epoch seconds. */
    private fun decodeJwtExpiry(token: String): Long {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return 0L
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP))
            val json = JSONObject(payload)
            json.optLong("exp", 0L)
        } catch (e: Exception) {
            0L
        }
    }

    private companion object {
        const val PREFS_NAME = "student_auth"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_EXPIRES_AT = "expires_at"
        const val KEY_AVATAR_URL = "avatar_url"
        const val KEY_PROFILE_JSON = "profile_json"
        const val KEY_SESSIONS_JSON = "sessions_json"
        const val KEY_FCM_TOKEN = "fcm_token"
    }
}

sealed class AuthEvent {
    data object SessionExpired : AuthEvent()
}
