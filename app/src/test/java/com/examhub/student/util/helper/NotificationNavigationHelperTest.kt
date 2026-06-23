package com.examhub.student.util.helper

import com.examhub.student.data.model.AppNotification
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNavigationHelperTest {
    @Test
    fun examStartedNotificationRoutesToExamDetail() {
        val destination = NotificationNavigationHelper.resolveDestination(
            notification(type = "exam-started", targetId = "exam-1")
        )

        assertEquals(
            NotificationNavigationHelper.Destination.ExamDetail("exam-1"),
            destination
        )
    }

    @Test
    fun examGradedNotificationRoutesToResultDetail() {
        val destination = NotificationNavigationHelper.resolveDestination(
            notification(type = "exam-graded", targetId = "sheet-1", entityId = "exam-1")
        )

        assertEquals(
            NotificationNavigationHelper.Destination.ResultDetail("sheet-1"),
            destination
        )
    }

    @Test
    fun appealRespondedNotificationRoutesToAppealDetail() {
        val destination = NotificationNavigationHelper.resolveDestination(
            notification(type = "appeal-responded", targetId = "appeal-1")
        )

        assertEquals(
            NotificationNavigationHelper.Destination.AppealDetail("appeal-1"),
            destination
        )
    }

    private fun notification(
        type: String,
        targetId: String? = null,
        entityId: String? = null
    ) = AppNotification(
        id = "notification-1",
        type = type,
        title = "Title",
        content = "Content",
        link = null,
        appealId = null,
        targetId = targetId,
        entityId = entityId,
        isRead = false,
        createdAt = "2026-06-13T00:00:00Z"
    )
}
