package com.examhub.student.util.extension

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun String?.toLocalDisplayDateTime(fallback: String = ""): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return fallback

    val date = parseServerDateTime(raw) ?: return raw.removeSuffix("Z")
    return SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(date)
}

private fun parseServerDateTime(raw: String): Date? {
    val normalized = raw.replace(Regex("(\\.\\d{3})\\d+(?=Z|[+-]\\d{2}:?\\d{2}$)"), "$1")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss"
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (!normalized.endsWith("Z") && !normalized.matches(Regex(".*[+-]\\d{2}:?\\d{2}$"))) {
                    timeZone = TimeZone.getDefault()
                }
            }.parse(normalized)
        }.getOrNull()
    }
}
