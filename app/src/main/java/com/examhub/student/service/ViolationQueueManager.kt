package com.examhub.student.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.worker.ViolationSyncWorker
import java.util.UUID

class ViolationQueueManager(
    context: Context,
    private val gson: Gson
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val itemType = object : TypeToken<List<QueuedViolation>>() {}.type

    @Synchronized
    fun enqueue(request: LockViolationRequest) {
        val items = readAll().toMutableList()
        val id = request.clientEventId ?: UUID.randomUUID().toString()
        items.add(
            QueuedViolation(
                id = id,
                request = request.copy(
                    clientEventId = id,
                    evidenceData = request.evidenceData + mapOf("client_event_id" to id)
                ),
                queuedAt = System.currentTimeMillis()
            )
        )
        writeAll(items.takeLast(MAX_QUEUE_SIZE))
        scheduleSync()
    }

    @Synchronized
    fun peekAll(): List<QueuedViolation> = readAll()

    @Synchronized
    fun remove(id: String) {
        writeAll(readAll().filterNot { it.id == id })
    }

    @Synchronized
    fun count(): Int = readAll().size

    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ViolationSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(prefsContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun readAll(): List<QueuedViolation> {
        val raw = prefs.getString(KEY_QUEUE, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { gson.fromJson<List<QueuedViolation>>(raw, itemType) }.getOrDefault(emptyList())
    }

    private fun writeAll(items: List<QueuedViolation>) {
        prefs.edit().putString(KEY_QUEUE, gson.toJson(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "lock_violation_queue"
        private const val KEY_QUEUE = "items"
        private const val MAX_QUEUE_SIZE = 200
        private const val WORK_NAME = "lock_violation_sync"
    }

    private val prefsContext = context.applicationContext
}

data class QueuedViolation(
    val id: String,
    val request: LockViolationRequest,
    val queuedAt: Long
)
