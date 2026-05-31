package com.examhub.student.service

import android.content.Context
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ActiveExamSession(
    val examId: String,
    val sessionId: String,
    val endTime: String,
    val remainingSeconds: Int,
    val savedAtMillis: Long,
    val isLockedMode: Boolean,
    val classCode: String = "",
    val studentCode: String = "",
    val studentCodeMode: String = "UNKNOWN",
    val questionCount: Int = 0
) {
    fun currentRemainingSeconds(): Int {
        val endMillis = parseTimeMillis(endTime)
        if (endMillis != null) {
            return ((endMillis - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
        }
        val elapsed = ((System.currentTimeMillis() - savedAtMillis) / 1_000L).toInt().coerceAtLeast(0)
        return (remainingSeconds - elapsed).coerceAtLeast(0)
    }

    fun isStillActive(): Boolean = sessionId.isNotBlank() && currentRemainingSeconds() > 0

    private fun parseTimeMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()
        }
    }
}

class ActiveExamSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(session: ActiveExamSession) {
        prefs.edit().putString(key(session.examId), gson.toJson(session)).apply()
    }

    fun get(examId: String): ActiveExamSession? {
        if (examId.isBlank()) return null
        val raw = prefs.getString(key(examId), null) ?: return null
        val session = runCatching { gson.fromJson(raw, ActiveExamSession::class.java) }.getOrNull()
        if (session == null || !session.isStillActive()) {
            clear(examId)
            return null
        }
        return session
    }

    fun clear(examId: String) {
        if (examId.isBlank()) return
        prefs.edit().remove(key(examId)).apply()
    }

    fun clearBySessionId(sessionId: String) {
        if (sessionId.isBlank()) return
        prefs.all.entries
            .filter { it.key.startsWith(KEY_PREFIX) && it.value is String }
            .firstOrNull { (_, value) ->
                runCatching { gson.fromJson(value as String, ActiveExamSession::class.java).sessionId == sessionId }
                    .getOrDefault(false)
            }
            ?.key
            ?.let { prefs.edit().remove(it).apply() }
    }

    private fun key(examId: String) = "$KEY_PREFIX$examId"

    private companion object {
        const val PREFS_NAME = "active_exam_sessions"
        const val KEY_PREFIX = "active_session_"
    }
}
