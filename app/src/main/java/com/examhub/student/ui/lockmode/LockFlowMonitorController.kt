package com.examhub.student.ui.lockmode

import android.content.Context
import com.examhub.student.R
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.repository.LockModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LockFlowMonitorController(
    private val context: Context,
    private val lockModeRepository: LockModeRepository,
    private val scope: CoroutineScope,
    private val sessionIdProvider: () -> String,
    private val screenName: String
) {
    private var monitor: LockModeMonitor? = null
    private var offlineEventOpen = false

    fun start() {
        if (monitor != null) return
        monitor = LockModeMonitor(
            context = context.applicationContext,
            onNetworkLost = {
                if (!offlineEventOpen) {
                    offlineEventOpen = true
                    queueViolation(
                        "network_offline",
                        mapOf(
                            "screen" to screenName,
                            "reason" to "network_offline",
                            "offline_started_at" to nowIso(),
                            "violation_label" to context.getString(R.string.lock_violation_network_lost_label),
                            "teacher_message" to context.getString(R.string.lock_violation_network_lost_teacher_message)
                        )
                    )
                }
            },
            onNetworkAvailable = {
                if (offlineEventOpen) {
                    offlineEventOpen = false
                    queueViolation(
                        "network_restored",
                        mapOf(
                            "screen" to screenName,
                            "reason" to "network_restored",
                            "restored_at" to nowIso(),
                            "violation_label" to context.getString(R.string.lock_violation_network_restored_label),
                            "teacher_message" to context.getString(R.string.lock_violation_network_restored_teacher_message)
                        )
                    )
                } else {
                    flushQueuedViolations()
                }
            },
            onScreenOff = {
                queueViolation(
                    "screen_off",
                    mapOf(
                        "screen" to screenName,
                        "violation_label" to context.getString(R.string.lock_violation_screen_off_label),
                        "teacher_message" to context.getString(R.string.lock_violation_screen_off_teacher_message)
                    )
                )
            }
        ).also { it.start() }
        flushQueuedViolations()
    }

    fun stop() {
        monitor?.stop()
        monitor = null
    }

    private fun queueViolation(type: String, evidence: Map<String, Any?>) {
        val sessionId = sessionIdProvider().takeIf { it.isNotBlank() } ?: return
        lockModeRepository.queueViolation(
            LockViolationRequest(
                sessionId = sessionId,
                violationType = type,
                occurredAt = nowIso(),
                evidenceData = evidence
            )
        )
        flushQueuedViolations()
    }

    private fun flushQueuedViolations() {
        scope.launch {
            lockModeRepository.flushQueuedViolations().collect { }
        }
    }

    private fun nowIso(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
    }
}
