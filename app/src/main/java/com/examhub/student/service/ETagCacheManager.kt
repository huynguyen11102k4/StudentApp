package com.examhub.student.service

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

class ETagCacheManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(url: String): CachedResponse? {
        val key = key(url)
        val etag = prefs.getString("$key.etag", null) ?: return null
        val body = prefs.getString("$key.body", null) ?: return null
        return CachedResponse(
            etag = etag,
            body = body,
            contentType = prefs.getString("$key.contentType", "application/json"),
            code = prefs.getInt("$key.code", 200),
            message = prefs.getString("$key.message", "OK") ?: "OK"
        )
    }

    fun put(url: String, etag: String, body: String, contentType: String?, code: Int, message: String) {
        val key = key(url)
        prefs.edit()
            .putString("$key.etag", etag)
            .putString("$key.body", body)
            .putString("$key.contentType", contentType)
            .putInt("$key.code", code)
            .putString("$key.message", message)
            .apply()
    }

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private companion object {
        const val PREFS_NAME = "etag_cache"
    }
}

data class CachedResponse(
    val etag: String,
    val body: String,
    val contentType: String?,
    val code: Int,
    val message: String
)
