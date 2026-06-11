package com.examhub.student.util.helper

import android.os.Bundle
import com.examhub.student.data.model.AppNotification
import com.google.gson.JsonObject
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
        "exam_assigned",
        "exam_closed"
    )
    private val resultTypes = setOf(
        "grade_updated",
        "result_ready",
        "exam_graded",
        "submission_scored",
        "submission_graded",
        "submission_pending"
    )

    sealed class Destination {
        data class AppealDetail(val appealId: String) : Destination()
        data class ResultDetail(val sheetId: String) : Destination()
        data class ExamDetail(val examId: String) : Destination()
        data object AppealsList : Destination()
        data object ResultsList : Destination()
        data object ExamList : Destination()
        data object Notifications : Destination()
        data object None : Destination()
    }

    fun resolveDestination(extras: Bundle?): Destination {
        if (extras == null) return Destination.None
        return resolve(
            PayloadFields(
                route = extras.firstString("route"),
                type = extras.firstString("type", "notification_type", "notificationType", "event_type", "eventType"),
                link = extras.firstString("link", "url", "deeplink", "deep_link"),
                targetId = extras.firstString("target_id", "targetId"),
                entityId = extras.firstString("entity_id", "entityId"),
                appealId = extras.firstString("appeal_id", "appealId"),
                sheetId = extras.firstString(
                    "sheet_id",
                    "sheetId",
                    "answer_sheet_id",
                    "answerSheetId",
                    "result_id",
                    "resultId",
                    "submission_id",
                    "submissionId"
                ),
                examId = extras.firstString("exam_id", "examId"),
                openedFromNotification = true
            )
        )
    }

    fun resolveDestination(notification: AppNotification): Destination {
        return resolve(
            PayloadFields(
                route = firstNotBlank(
                    notification.route,
                    notification.metadata.stringValue("route"),
                    notification.data.stringValue("route")
                ),
                type = firstNotBlank(
                    notification.type,
                    notification.metadata.stringValue("type"),
                    notification.metadata.stringValue("notification_type"),
                    notification.metadata.stringValue("notificationType"),
                    notification.metadata.stringValue("event_type"),
                    notification.metadata.stringValue("eventType"),
                    notification.data.stringValue("type"),
                    notification.data.stringValue("notification_type"),
                    notification.data.stringValue("notificationType"),
                    notification.data.stringValue("event_type"),
                    notification.data.stringValue("eventType")
                ),
                link = firstNotBlank(
                    notification.link,
                    notification.metadata.stringValue("link"),
                    notification.metadata.stringValue("url"),
                    notification.metadata.stringValue("deeplink"),
                    notification.metadata.stringValue("deep_link"),
                    notification.data.stringValue("link"),
                    notification.data.stringValue("url"),
                    notification.data.stringValue("deeplink"),
                    notification.data.stringValue("deep_link")
                ),
                targetId = firstNotBlank(
                    notification.targetId,
                    notification.metadata.stringValue("target_id"),
                    notification.metadata.stringValue("targetId"),
                    notification.data.stringValue("target_id"),
                    notification.data.stringValue("targetId")
                ),
                entityId = firstNotBlank(
                    notification.entityId,
                    notification.metadata.stringValue("entity_id"),
                    notification.metadata.stringValue("entityId"),
                    notification.data.stringValue("entity_id"),
                    notification.data.stringValue("entityId")
                ),
                appealId = firstNotBlank(
                    notification.appealId,
                    notification.metadata.stringValue("appeal_id"),
                    notification.metadata.stringValue("appealId"),
                    notification.data.stringValue("appeal_id"),
                    notification.data.stringValue("appealId")
                ),
                sheetId = firstNotBlank(
                    notification.metadata.stringValue("sheet_id"),
                    notification.metadata.stringValue("sheetId"),
                    notification.metadata.stringValue("answer_sheet_id"),
                    notification.metadata.stringValue("answerSheetId"),
                    notification.metadata.stringValue("result_id"),
                    notification.metadata.stringValue("resultId"),
                    notification.metadata.stringValue("submission_id"),
                    notification.metadata.stringValue("submissionId"),
                    notification.data.stringValue("sheet_id"),
                    notification.data.stringValue("sheetId"),
                    notification.data.stringValue("answer_sheet_id"),
                    notification.data.stringValue("answerSheetId"),
                    notification.data.stringValue("result_id"),
                    notification.data.stringValue("resultId"),
                    notification.data.stringValue("submission_id"),
                    notification.data.stringValue("submissionId")
                ),
                examId = firstNotBlank(
                    notification.metadata.stringValue("exam_id"),
                    notification.metadata.stringValue("examId"),
                    notification.data.stringValue("exam_id"),
                    notification.data.stringValue("examId")
                ),
                openedFromNotification = false
            )
        )
    }

    fun isAppealNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("appeal_detail", "appeal", "appeals") ||
            type.matchesAny(appealTypes) ||
            type.normalizedRouteOrType()?.startsWith("appeal_") == true ||
            link.containsAppealLink()
    }

    fun isResultNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("result_detail", "result", "results", "grading_detail", "sheet_detail") ||
            route.matchesAny("submission_pending", "submission", "submissions") ||
            type.matchesAny(resultTypes) ||
            type.orEmpty().contains("submission", ignoreCase = true) ||
            type.orEmpty().contains("grade", ignoreCase = true) ||
            type.orEmpty().contains("result", ignoreCase = true) ||
            link.containsResultLink()
    }

    fun isExamNotification(route: String?, type: String?, link: String?): Boolean {
        return route.matchesAny("exam_detail", "exam", "exams", "exam_results") ||
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
        return null
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

    private fun resolve(fields: PayloadFields): Destination {
        val route = fields.route
        val type = fields.type
        val link = fields.link
        val routeIsAppeal = route.matchesAny("appeal_detail", "appeal")
        val routeIsResult = route.matchesAny("result_detail", "result", "grading_detail", "sheet_detail")
        val routeIsExam = route.matchesAny("exam_detail", "exam", "exam_results")

        val appealId = firstNotBlank(
            fields.appealId,
            extractIdFromLink(link, "appeal_id", "appealId"),
            if (isAppealNotification(route, type, link)) fields.targetId else null,
            if (isAppealNotification(route, type, link)) fields.entityId else null,
            if (isAppealNotification(route, type, link)) extractPathIdAfter(link, "appeals", "appeal") else null,
            if (isAppealNotification(route, type, link)) extractLastId(link) else null
        )

        val sheetId = firstNotBlank(
            fields.sheetId,
            extractIdFromLink(
                link,
                "sheet_id",
                "sheetId",
                "answer_sheet_id",
                "answerSheetId",
                "result_id",
                "resultId",
                "submission_id",
                "submissionId"
            ),
            if (isResultNotification(route, type, link)) fields.targetId else null,
            if (isResultNotification(route, type, link)) fields.entityId else null,
            if (isResultNotification(route, type, link)) extractPathIdAfter(
                link,
                "results",
                "result",
                "sheets",
                "answer-sheets",
                "answer_sheets",
                "grading",
                "submissions",
                "submission"
            ) else null,
            if (isResultNotification(route, type, link)) extractLastId(link) else null
        )

        val examId = firstNotBlank(
            fields.examId,
            extractIdFromLink(link, "exam_id", "examId"),
            if (isExamNotification(route, type, link)) fields.targetId else null,
            if (isExamNotification(route, type, link)) fields.entityId else null,
            if (isExamNotification(route, type, link)) extractPathIdAfter(link, "exams", "exam") else null
        )

        val isAppeal = isAppealNotification(route, type, link) || (!appealId.isNullOrBlank() && !routeIsResult)
        val isResult = isResultNotification(route, type, link) || !sheetId.isNullOrBlank()
        val isExam = isExamNotification(route, type, link) || (!examId.isNullOrBlank() && !isAppeal && !isResult)

        return when {
            isAppeal && !appealId.isNullOrBlank() -> Destination.AppealDetail(appealId)
            isResult && !sheetId.isNullOrBlank() -> Destination.ResultDetail(sheetId)
            isResult && !examId.isNullOrBlank() -> Destination.ResultsList
            isExam && !examId.isNullOrBlank() -> Destination.ExamDetail(examId)
            route.matchesAny("appeals") || isAppeal -> Destination.AppealsList
            route.matchesAny("results") || isResult -> Destination.ResultsList
            route.matchesAny("exams") || isExam -> Destination.ExamList
            route.matchesAny("notifications") || fields.openedFromNotification -> Destination.Notifications
            routeIsAppeal -> Destination.AppealsList
            routeIsResult -> Destination.ResultsList
            routeIsExam -> Destination.ExamList
            else -> Destination.None
        }
    }

    private fun Bundle.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            getString(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun JsonObject?.stringValue(key: String): String? {
        val element = this?.get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return element.asString.takeIf { it.isNotBlank() }
    }

    private fun firstNotBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun extractPathIdAfter(link: String?, vararg segments: String): String? {
        val raw = link?.takeIf { it.isNotBlank() } ?: return null
        val parts = raw
            .split('/', '?', '#')
            .map(::decode)
            .filter { it.isNotBlank() }
        val normalizedSegments = segments.map { it.lowercase(Locale.ROOT) }.toSet()
        val index = parts.indexOfLast {
            it.lowercase(Locale.ROOT).trim('_', '-') in normalizedSegments
        }
        return parts.getOrNull(index + 1)
            ?.takeIf { it.isNotBlank() && !it.contains("=") }
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
            raw.contains("/grading/", ignoreCase = true) ||
            raw.contains("/grading?", ignoreCase = true) ||
            raw.contains("/answer-sheets/", ignoreCase = true) ||
            raw.contains("/answer_sheets/", ignoreCase = true) ||
            raw.contains("/sheets/", ignoreCase = true) ||
            raw.contains("/submissions/", ignoreCase = true) ||
            raw.contains("/submissions?", ignoreCase = true) ||
            raw.contains("resultId=", ignoreCase = true) ||
            raw.contains("result_id=", ignoreCase = true) ||
            raw.contains("sheetId=", ignoreCase = true) ||
            raw.contains("sheet_id=", ignoreCase = true) ||
            raw.contains("answerSheetId=", ignoreCase = true) ||
            raw.contains("answer_sheet_id=", ignoreCase = true) ||
            raw.contains("submissionId=", ignoreCase = true) ||
            raw.contains("submission_id=", ignoreCase = true)
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

    private data class PayloadFields(
        val route: String?,
        val type: String?,
        val link: String?,
        val targetId: String?,
        val entityId: String?,
        val appealId: String?,
        val sheetId: String?,
        val examId: String?,
        val openedFromNotification: Boolean
    )
}
