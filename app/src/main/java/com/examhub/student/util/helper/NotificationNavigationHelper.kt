package com.examhub.student.util.helper

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object NotificationNavigationHelper {
    private val appealTypes = setOf(
        "appeal_created",
        "appeal_updated",
        "appeal_new",
        "appeal_resolved",
        "appeal_replied"
    )
    private val examTypes = setOf(
        "exam_created",
        "exam_opened",
        "exam_upcoming",
        "exam_reminder",
        "exam_assigned"
    )
    private val resultTypes = setOf(
        "grade_updated",
        "result_ready",
        "exam_graded"
    )

    fun isAppealNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("appeal_detail", "appeal", "appeals") ||
            type.matchesAny(appealTypes) ||
            link.containsAppealLink()
    }

    fun isResultNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("result_detail", "result", "results") ||
            type.matchesAny(resultTypes) ||
            type.orEmpty().contains("grade", ignoreCase = true) ||
            type.orEmpty().contains("result", ignoreCase = true) ||
            link.containsResultLink()
    }

    fun isExamNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("exam_detail", "exam", "exams") ||
            type.matchesAny(examTypes) ||
            link.containsExamLink()
    }

    fun extractIdFromLink(link: String?, vararg keys: String): String? {
        val raw = link?.takeIf { it.isNotBlank() } ?: return null
        keys.forEach { key ->
            Regex("(?i)(?:[?&])${Regex.escape(key)}=([^&#]+)")
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::decode)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return extractLastId(raw)
    }

    fun extractLastId(value: String?): String? {
        val raw = value?.takeIf { it.isNotBlank() } ?: return null
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            .findAll(raw)
            .lastOrNull()
            ?.value
            ?.let { return it }
        return raw.split('/', '?', '#', '&', '=')
            .asReversed()
            .firstOrNull { it.length >= 16 && it.any(Char::isDigit) }
            ?.let(::decode)
    }

    private fun String?.matchesAny(vararg candidates: String): Boolean {
        val normalized = this.normalizedRouteOrType() ?: return false
        return candidates.any { normalized == it }
    }

    private fun String?.matchesAny(candidates: Set<String>): Boolean {
        val normalized = this.normalizedRouteOrType() ?: return false
        return normalized in candidates
    }

    private fun String?.normalizedRouteOrType(): String? {
        return this?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.ROOT)
            ?.replace('-', '_')
    }

    private fun String?.containsAppealLink(): Boolean {
        val raw = this ?: return false
        return raw.contains("/appeals/", ignoreCase = true) ||
            raw.contains("/appeals?", ignoreCase = true) ||
            raw.contains("appealId=", ignoreCase = true) ||
            raw.contains("appeal_id=", ignoreCase = true)
    }

    private fun String?.containsResultLink(): Boolean {
        val raw = this ?: return false
        return raw.contains("/results/", ignoreCase = true) ||
            raw.contains("/results?", ignoreCase = true) ||
            raw.contains("resultId=", ignoreCase = true) ||
            raw.contains("result_id=", ignoreCase = true) ||
            raw.contains("sheetId=", ignoreCase = true) ||
            raw.contains("sheet_id=", ignoreCase = true) ||
            raw.contains("answerSheetId=", ignoreCase = true) ||
            raw.contains("answer_sheet_id=", ignoreCase = true)
    }

    private fun String?.containsExamLink(): Boolean {
        val raw = this ?: return false
        return raw.contains("/exams/", ignoreCase = true) ||
            raw.contains("/exams?", ignoreCase = true) ||
            raw.contains("examId=", ignoreCase = true) ||
            raw.contains("exam_id=", ignoreCase = true)
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
