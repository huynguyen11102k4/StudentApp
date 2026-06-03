package com.examhub.student.util.extension

import com.examhub.student.OmrApplication
import com.examhub.student.R
import java.util.Locale

private val technicalLabelMap = mapOf(
    "READY_FOR_SHUFFLE" to R.string.friendly_ready_for_shuffle,
    "TEACHER_GRADING" to R.string.friendly_teacher_grading,
    "STUDENT_SUBMISSION" to R.string.friendly_student_submission,
    "AUTO_GRADING" to R.string.friendly_auto_grading,
    "DRAFT" to R.string.friendly_draft,
    "SCHEDULED" to R.string.friendly_scheduled,
    "ACTIVE" to R.string.friendly_active,
    "END" to R.string.friendly_end,
    "PROCESSING" to R.string.friendly_processing,
    "CLOSED" to R.string.friendly_closed,
    "DONE" to R.string.friendly_done,
    "COMPLETED" to R.string.friendly_done,
    "PUBLISHED" to R.string.friendly_published,
    "CANCELLED" to R.string.friendly_cancelled,
    "PENDING" to R.string.friendly_pending,
    "GRADED" to R.string.friendly_graded,
    "RESOLVED" to R.string.friendly_resolved,
    "ACCEPTED" to R.string.friendly_accepted,
    "REVIEWED" to R.string.friendly_resolved,
    "REJECTED" to R.string.friendly_rejected,
    "SUBMITTED" to R.string.friendly_submitted,
    "NOT_SUBMITTED" to R.string.friendly_not_submitted,
    "OPEN" to R.string.friendly_open,
    "READY" to R.string.friendly_ready,
    "LOCKED" to R.string.friendly_locked,
    "EXAM" to R.string.friendly_exam,
    "PRACTICE" to R.string.friendly_practice,
    "QUIZ" to R.string.friendly_quiz,
    "MIDTERM" to R.string.friendly_midterm,
    "FINAL" to R.string.friendly_final,
    "SUBMISSION_PENDING" to R.string.friendly_submission_pending,
    "APPEAL_NEW" to R.string.friendly_appeal_new,
    "APPEAL_CREATED" to R.string.friendly_appeal_new,
    "APPEAL_UPDATED" to R.string.friendly_appeal_updated,
    "EXAM_CREATED" to R.string.friendly_exam_created,
    "EXAM_CLOSED" to R.string.friendly_exam_closed,
    "CLASS_INVITE" to R.string.friendly_class_invite,
    "SYSTEM" to R.string.friendly_system
)

private val viLocale = Locale.forLanguageTag("vi-VN")

fun String?.toFriendlyExamStatus(): String = friendlyEnumLabel(
    value = this,
    fallbackRes = R.string.friendly_unknown
)

fun String?.toFriendlyGradingType(): String = friendlyEnumLabel(
    value = this,
    fallbackRes = null,
    overrides = mapOf(
        "TEACHER" to R.string.friendly_teacher_grading,
        "MANUAL" to R.string.friendly_teacher_grading,
        "CAMERA" to R.string.friendly_teacher_grading,
        "OFFLINE" to R.string.friendly_teacher_grading,
        "STUDENT" to R.string.friendly_student_submission,
        "SUBMISSION" to R.string.friendly_student_submission,
        "ONLINE" to R.string.friendly_student_submission,
        "SERVER" to R.string.friendly_student_submission
    )
)

fun String?.toFriendlyExamType(): String = friendlyEnumLabel(this, fallbackRes = null)

fun String?.toFriendlySubmissionStatus(): String = friendlyEnumLabel(
    value = this,
    fallbackRes = R.string.friendly_unknown,
    overrides = mapOf(
        "WAITING" to R.string.friendly_waiting,
        "PENDING" to R.string.friendly_pending_ungraded,
        "REVIEWING" to R.string.friendly_reviewing,
        "FAILED" to R.string.friendly_failed
    )
)

fun String?.toFriendlyAppealStatus(): String = friendlyEnumLabel(
    value = this,
    fallbackRes = R.string.friendly_unknown,
    overrides = mapOf(
        "PENDING" to R.string.friendly_pending,
        "ACCEPTED" to R.string.friendly_accepted,
        "REVIEWED" to R.string.friendly_resolved,
        "RESOLVED" to R.string.friendly_resolved,
        "REJECTED" to R.string.friendly_rejected
    )
)

fun String?.toFriendlyAppealItemStatus(): String = friendlyEnumLabel(
    value = this,
    fallbackRes = R.string.friendly_no_note,
    overrides = mapOf(
        "PENDING" to R.string.friendly_appeal_pending_note,
        "ACCEPTED" to R.string.friendly_accepted,
        "REVIEWED" to R.string.friendly_resolved,
        "RESOLVED" to R.string.friendly_resolved,
        "REJECTED" to R.string.friendly_rejected
    )
)

fun String?.toFriendlyNotificationType(): String =
    friendlyEnumLabel(this, fallbackRes = R.string.friendly_notification)

fun String.replaceTechnicalLabels(): String {
    if (isBlank()) return this
    return technicalLabelMap.entries
        .sortedByDescending { it.key.length }
        .fold(this) { text, (technical, friendlyRes) ->
            text.replace(
                Regex("\\b${Regex.escape(technical)}\\b", RegexOption.IGNORE_CASE),
                localized(friendlyRes)
            )
        }
}

private fun friendlyEnumLabel(
    value: String?,
    fallbackRes: Int?,
    overrides: Map<String, Int> = emptyMap()
): String {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return fallbackRes?.let(::localized).orEmpty()

    val normalized = raw.normalizeEnumKey()
    return (overrides[normalized] ?: technicalLabelMap[normalized])?.let(::localized)
        ?: raw.takeUnless { it.looksTechnicalEnum() }
        ?: normalized
            .lowercase(viLocale)
            .replace('_', ' ')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(viLocale) else char.toString()
            }
}

private fun localized(resId: Int): String = OmrApplication.appContext.getString(resId)

private fun String.normalizeEnumKey(): String {
    return trim()
        .replace("-", "_")
        .replace(" ", "_")
        .uppercase(Locale.US)
}

private fun String.looksTechnicalEnum(): Boolean {
    return any { it == '_' } || all { !it.isLetter() || it.isUpperCase() }
}
