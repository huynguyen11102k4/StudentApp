package com.examhub.student.extension

import java.util.Locale

private val technicalLabelMap = mapOf(
    "READY_FOR_SHUFFLE" to "Sẵn sàng tạo đề",
    "TEACHER_GRADING" to "Giáo viên chấm",
    "STUDENT_SUBMISSION" to "Học sinh nộp bài",
    "AUTO_GRADING" to "Tự động chấm",
    "DRAFT" to "Bản nháp",
    "SCHEDULED" to "Đã lên lịch",
    "ACTIVE" to "Đang diễn ra",
    "PROCESSING" to "Đang xử lý",
    "CLOSED" to "Đã kết thúc",
    "DONE" to "Đã hoàn tất",
    "COMPLETED" to "Đã hoàn tất",
    "PUBLISHED" to "Đã phát hành",
    "CANCELLED" to "Đã hủy",
    "PENDING" to "Đang xử lý",
    "GRADED" to "Đã chấm",
    "RESOLVED" to "Đã phản hồi",
    "ACCEPTED" to "Đã chấp nhận",
    "REVIEWED" to "Đã phản hồi",
    "REJECTED" to "Bị từ chối",
    "SUBMITTED" to "Đã nộp",
    "NOT_SUBMITTED" to "Chưa nộp",
    "OPEN" to "Đang mở",
    "READY" to "Sẵn sàng",
    "LOCKED" to "Đã khóa",
    "EXAM" to "Bài thi",
    "PRACTICE" to "Bài luyện tập",
    "QUIZ" to "Bài kiểm tra nhanh",
    "MIDTERM" to "Giữa kỳ",
    "FINAL" to "Cuối kỳ",
    "SUBMISSION_PENDING" to "Có bài nộp cần chấm",
    "APPEAL_NEW" to "Có khiếu nại mới",
    "APPEAL_CREATED" to "Có khiếu nại mới",
    "APPEAL_UPDATED" to "Khiếu nại được cập nhật",
    "EXAM_CREATED" to "Bài thi mới",
    "EXAM_CLOSED" to "Bài thi đã kết thúc",
    "CLASS_INVITE" to "Lời mời lớp học",
    "SYSTEM" to "Thông báo hệ thống"
)

private val viLocale = Locale.forLanguageTag("vi-VN")

fun String?.toFriendlyExamStatus(): String = friendlyEnumLabel(this, fallback = "Chưa xác định")

fun String?.toFriendlyGradingType(): String = friendlyEnumLabel(
    value = this,
    fallback = "",
    overrides = mapOf(
        "TEACHER" to "Giáo viên chấm",
        "MANUAL" to "Giáo viên chấm",
        "CAMERA" to "Giáo viên chấm",
        "OFFLINE" to "Giáo viên chấm",
        "STUDENT" to "Học sinh nộp bài",
        "SUBMISSION" to "Học sinh nộp bài",
        "ONLINE" to "Học sinh nộp bài",
        "SERVER" to "Học sinh nộp bài"
    )
)

fun String?.toFriendlyExamType(): String = friendlyEnumLabel(this, fallback = "")

fun String?.toFriendlySubmissionStatus(): String = friendlyEnumLabel(
    value = this,
    fallback = "Chưa xác định",
    overrides = mapOf(
        "WAITING" to "Chờ chấm",
        "PENDING" to "Chưa chấm",
        "REVIEWING" to "Đang xem lại",
        "FAILED" to "Xử lý thất bại"
    )
)

fun String?.toFriendlyAppealStatus(): String = friendlyEnumLabel(
    value = this,
    fallback = "Chưa xác định",
    overrides = mapOf(
        "PENDING" to "Đang xử lý",
        "ACCEPTED" to "Đã chấp nhận",
        "REVIEWED" to "Đã phản hồi",
        "RESOLVED" to "Đã phản hồi",
        "REJECTED" to "Bị từ chối"
    )
)

fun String?.toFriendlyAppealItemStatus(): String = friendlyEnumLabel(
    value = this,
    fallback = "Chưa có ghi chú",
    overrides = mapOf(
        "PENDING" to "Đang chờ giáo viên xem lại",
        "ACCEPTED" to "Đã chấp nhận",
        "REVIEWED" to "Đã phản hồi",
        "RESOLVED" to "Đã phản hồi",
        "REJECTED" to "Bị từ chối"
    )
)

fun String?.toFriendlyNotificationType(): String = friendlyEnumLabel(this, fallback = "Thông báo")

fun String.replaceTechnicalLabels(): String {
    if (isBlank()) return this
    return technicalLabelMap.entries
        .sortedByDescending { it.key.length }
        .fold(this) { text, (technical, friendly) ->
            text.replace(Regex("\\b${Regex.escape(technical)}\\b", RegexOption.IGNORE_CASE), friendly)
        }
}

private fun friendlyEnumLabel(
    value: String?,
    fallback: String,
    overrides: Map<String, String> = emptyMap()
): String {
    val raw = value.orEmpty().trim()
    if (raw.isBlank()) return fallback

    val normalized = raw.normalizeEnumKey()
    return overrides[normalized]
        ?: technicalLabelMap[normalized]
        ?: raw.takeUnless { it.looksTechnicalEnum() }
        ?: normalized
            .lowercase(viLocale)
            .replace('_', ' ')
            .replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(viLocale) else char.toString()
            }
}

private fun String.normalizeEnumKey(): String {
    return trim()
        .replace("-", "_")
        .replace(" ", "_")
        .uppercase(Locale.US)
}

private fun String.looksTechnicalEnum(): Boolean {
    return any { it == '_' } || all { !it.isLetter() || it.isUpperCase() }
}
