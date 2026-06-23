package com.examhub.student.util.helper

import android.app.Application
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PushNotificationHandoffTest {
    @Test
    fun coldStartRoutesEachNotificationExactlyOnceAfterSplash() {
        cases.forEach { case ->
            val handoff = handoff()

            assertTrue(handoff.capture(case.extras))
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(SPLASH, hasToken = true))
            assertEquals(
                PushNotificationHandoff.Action.NavigateDestination(case.destination),
                handoff.nextAction(DASHBOARD, hasToken = true)
            )
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(DASHBOARD, hasToken = true))
        }
    }

    @Test
    fun alreadyOpenRoutesEachNotificationExactlyOnce() {
        cases.forEach { case ->
            val handoff = handoff()

            assertTrue(handoff.capture(case.extras))
            assertEquals(
                PushNotificationHandoff.Action.NavigateDestination(case.destination),
                handoff.nextAction(DASHBOARD, hasToken = true)
            )
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(DASHBOARD, hasToken = true))
        }
    }

    @Test
    fun loggedOutUserNavigatesToLoginThenRoutesEachNotificationExactlyOnceAfterLogin() {
        cases.forEach { case ->
            val handoff = handoff()

            assertTrue(handoff.capture(case.extras))
            assertEquals(
                PushNotificationHandoff.Action.NavigateLogin,
                handoff.nextAction(DASHBOARD, hasToken = false)
            )
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(LOGIN, hasToken = false))
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(LOGIN, hasToken = true))
            assertEquals(
                PushNotificationHandoff.Action.NavigateDestination(case.destination),
                handoff.nextAction(DASHBOARD, hasToken = true)
            )
            assertEquals(PushNotificationHandoff.Action.None, handoff.nextAction(DASHBOARD, hasToken = true))
        }
    }

    private fun handoff() = PushNotificationHandoff(
        splashDestinationId = SPLASH,
        authDestinationIds = setOf(LOGIN, REGISTER, FORGOT_PASSWORD)
    )

    private data class Case(
        val extras: Bundle,
        val destination: NotificationNavigationHelper.Destination
    )

    private companion object {
        const val SPLASH = 1
        const val LOGIN = 2
        const val REGISTER = 3
        const val FORGOT_PASSWORD = 4
        const val DASHBOARD = 5

        val cases = listOf(
            Case(
                extras = Bundle().apply {
                    putString("type", "exam-started")
                    putString("target_id", "exam-1")
                },
                destination = NotificationNavigationHelper.Destination.ExamDetail("exam-1")
            ),
            Case(
                extras = Bundle().apply {
                    putString("type", "exam-graded")
                    putString("target_id", "sheet-1")
                    putString("entity_id", "exam-1")
                },
                destination = NotificationNavigationHelper.Destination.ResultDetail("sheet-1")
            ),
            Case(
                extras = Bundle().apply {
                    putString("type", "appeal-responded")
                    putString("target_id", "appeal-1")
                },
                destination = NotificationNavigationHelper.Destination.AppealDetail("appeal-1")
            )
        )
    }
}
