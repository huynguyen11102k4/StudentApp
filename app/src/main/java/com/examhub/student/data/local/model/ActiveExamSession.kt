package com.examhub.student.data.local.model

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
    val questionCount: Int = 0,
    val deviceId: String = "",
    val offlineDeadlineAt: String? = null,
    val offlineSyncDeadlineAt: String? = null,
    val requiresClientSubmissionId: Boolean = false
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
