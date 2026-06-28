package com.examhub.student.util.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextResolverTest {
    @Test
    fun localizationKeysWinOverRawNotificationText() {
        val text = NotificationTextResolver.resolve(
            NotificationTextResolver.Payload(
                type = "exam-started",
                notificationTitle = "Raw title",
                notificationBody = "Raw body",
                titleLocKey = "notif-title-exam-started",
                bodyLocKey = "notif_body_exam_started",
                bodyLocArgs = listOf("Midterm", "Math")
            ),
            fakeStrings
        )

        assertEquals("Exam started", text.title)
        assertEquals("Exam \"Midterm\" for Math has started.", text.body)
    }

    @Test
    fun defaultTextCoversNewNotificationTypes() {
        val examStarted = NotificationTextResolver.resolve(
            NotificationTextResolver.Payload(type = "exam-started"),
            fakeStrings
        )
        val examGraded = NotificationTextResolver.resolve(
            NotificationTextResolver.Payload(type = "exam-graded"),
            fakeStrings
        )
        val appealResponded = NotificationTextResolver.resolve(
            NotificationTextResolver.Payload(type = "appeal-responded"),
            fakeStrings
        )

        assertEquals("Exam started", examStarted.title)
        assertEquals("Exam \"%1\$s\" for %2\$s has started.", examStarted.body)
        assertEquals("Exam graded", examGraded.title)
        assertEquals("Your exam sheet has been graded. Total score: %1\$s", examGraded.body)
        assertEquals("Appeal responded", appealResponded.title)
        assertEquals("The teacher has responded to your appeal for exam %1\$s.", appealResponded.body)
    }

    private val fakeStrings = object : NotificationTextResolver.Strings {
        private val strings = mapOf(
            "notif_title_exam_started" to "Exam started",
            "notif_body_exam_started" to "Exam \"%1\$s\" for %2\$s has started.",
            "notif_title_exam_graded" to "Exam graded",
            "notif_body_exam_graded" to "Your exam sheet has been graded. Total score: %1\$s",
            "notif_title_appeal_responded" to "Appeal responded",
            "notif_body_appeal_responded" to "The teacher has responded to your appeal for exam %1\$s."
        )

        override fun resolve(key: String, args: List<String>): String? {
            val template = strings[key] ?: return null
            return if (args.isEmpty()) template else String.format(template, *args.toTypedArray())
        }

        override fun defaultTitle(type: String): String =
            when (NotificationTextResolver.normalizeType(type)) {
                "exam_started" -> strings.getValue("notif_title_exam_started")
                "exam_graded" -> strings.getValue("notif_title_exam_graded")
                "appeal_responded" -> strings.getValue("notif_title_appeal_responded")
                else -> "Notifications"
            }

        override fun defaultBody(type: String): String =
            when (NotificationTextResolver.normalizeType(type)) {
                "exam_started" -> strings.getValue("notif_body_exam_started")
                "exam_graded" -> strings.getValue("notif_body_exam_graded")
                "appeal_responded" -> strings.getValue("notif_body_appeal_responded")
                else -> "You have a new notification."
            }
    }
}
