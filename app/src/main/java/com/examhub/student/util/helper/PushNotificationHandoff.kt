package com.examhub.student.util.helper

import android.os.Bundle

class PushNotificationHandoff(
    private val splashDestinationId: Int,
    private val authDestinationIds: Set<Int>
) {
    private var pendingExtras: Bundle? = null

    fun capture(extras: Bundle?): Boolean {
        val source = extras ?: return false
        if (NotificationNavigationHelper.resolveDestination(source) == NotificationNavigationHelper.Destination.None) {
            return false
        }
        pendingExtras = Bundle(source)
        return true
    }

    fun nextAction(currentDestinationId: Int?, hasToken: Boolean): Action {
        val extras = pendingExtras ?: return Action.None
        val destinationId = currentDestinationId ?: return Action.None
        if (destinationId == splashDestinationId) return Action.None
        if (!hasToken) {
            return if (destinationId in authDestinationIds) Action.None else Action.NavigateLogin
        }
        if (destinationId in authDestinationIds) return Action.None

        val destination = NotificationNavigationHelper.resolveDestination(extras)
        pendingExtras = null
        return if (destination == NotificationNavigationHelper.Destination.None) {
            Action.None
        } else {
            Action.NavigateDestination(destination)
        }
    }

    sealed class Action {
        data object None : Action()
        data object NavigateLogin : Action()
        data class NavigateDestination(
            val destination: NotificationNavigationHelper.Destination
        ) : Action()
    }
}
