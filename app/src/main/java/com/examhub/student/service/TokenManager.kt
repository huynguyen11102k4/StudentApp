package com.examhub.student.service

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.JsonCacheEntity
import com.examhub.student.security.KeystoreCrypto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class TokenManager(
    context: Context,
    private val crypto: KeystoreCrypto,
    private val database: StudentAppDatabase
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val cacheDao = database.studentCacheDao()
    private val _authEvents = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AuthEvent> = _authEvents.asSharedFlow()

    init {
        importLegacyCacheOnce()
    }

    fun getAccessToken(): String? = getSecureString(KEY_ACCESS_TOKEN)

    fun getRefreshToken(): String? = getSecureString(KEY_REFRESH_TOKEN)

    fun getExpiresAt(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun saveTokens(accessToken: String, refreshToken: String) {
        val expiresAt = decodeJwtExpiry(accessToken)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, encryptValue(accessToken))
            .putString(KEY_REFRESH_TOKEN, encryptValue(refreshToken))
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun getCachedAvatarUrl(): String? = getCache(KEY_AVATAR_URL)

    fun saveCachedAvatarUrl(avatarUrl: String?) {
        if (avatarUrl.isNullOrBlank()) {
            removeCache(KEY_AVATAR_URL)
        } else {
            putCache(KEY_AVATAR_URL, avatarUrl)
        }
    }

    fun getCachedProfileJson(): String? = getCache(KEY_PROFILE_JSON)

    fun saveCachedProfileJson(profileJson: String?) {
        if (profileJson.isNullOrBlank()) {
            removeCache(KEY_PROFILE_JSON)
        } else {
            putCache(KEY_PROFILE_JSON, profileJson)
        }
    }

    fun getCachedSessionsJson(): String? = getCache(KEY_SESSIONS_JSON)

    fun saveCachedSessionsJson(sessionsJson: String?) {
        if (sessionsJson.isNullOrBlank()) {
            removeCache(KEY_SESSIONS_JSON)
        } else {
            putCache(KEY_SESSIONS_JSON, sessionsJson)
        }
    }

    fun saveFcmToken(token: String?) {
        val editor = prefs.edit()
        if (token.isNullOrBlank()) {
            editor.remove(KEY_FCM_TOKEN)
        } else {
            editor.putString(KEY_FCM_TOKEN, encryptValue(token))
        }
        editor.apply()
    }

    fun getFcmToken(): String? = getSecureString(KEY_FCM_TOKEN)

    fun clearTokens(notifySessionExpired: Boolean = false) {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_FCM_TOKEN)
            .apply()
        dbCall { cacheDao.deleteJsonNamespace(NAMESPACE_AUTH_CACHE) }
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

    private fun getSecureString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        if (stored.startsWith(ENCRYPTED_PREFIX)) {
            return runCatching { crypto.decryptString(stored.removePrefix(ENCRYPTED_PREFIX)) }.getOrNull()
        }
        // One-time migration from the previous plaintext storage.
        prefs.edit().putString(key, encryptValue(stored)).apply()
        return stored
    }

    private fun encryptValue(value: String): String = ENCRYPTED_PREFIX + crypto.encryptString(value)

    private fun getCache(key: String): String? =
        dbCall { cacheDao.getJson(NAMESPACE_AUTH_CACHE, key)?.json }

    private fun putCache(key: String, value: String) {
        dbCall {
            cacheDao.upsertJson(
                JsonCacheEntity(
                    namespace = NAMESPACE_AUTH_CACHE,
                    recordKey = key,
                    json = value,
                    sortOrder = 0,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun removeCache(key: String) {
        dbCall { cacheDao.deleteJson(NAMESPACE_AUTH_CACHE, key) }
    }

    private fun importLegacyCacheOnce() {
        dbCall {
            if (cacheDao.getMetadata(KEY_LEGACY_CACHE_IMPORT_COMPLETE) == "1") return@dbCall
            database.runInTransaction {
                listOf(KEY_AVATAR_URL, KEY_PROFILE_JSON, KEY_SESSIONS_JSON).forEach { key ->
                    prefs.getString(key, null)?.let { value ->
                        cacheDao.upsertJson(
                            JsonCacheEntity(
                                namespace = NAMESPACE_AUTH_CACHE,
                                recordKey = key,
                                json = value,
                                sortOrder = 0,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                cacheDao.upsertMetadata(
                    CacheMetadataEntity(
                        KEY_LEGACY_CACHE_IMPORT_COMPLETE,
                        "1",
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

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
        const val ENCRYPTED_PREFIX = "enc:v1:"
        const val NAMESPACE_AUTH_CACHE = "auth_cache"
        const val KEY_LEGACY_CACHE_IMPORT_COMPLETE = "legacy_auth_cache_import_complete"
    }
}

sealed class AuthEvent {
    data object SessionExpired : AuthEvent()
}
