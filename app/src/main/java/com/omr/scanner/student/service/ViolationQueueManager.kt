package com.omr.scanner.student.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.omr.scanner.student.model.request.LockViolationRequest
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
        items.add(
            QueuedViolation(
                id = UUID.randomUUID().toString(),
                request = request,
                queuedAt = System.currentTimeMillis()
            )
        )
        writeAll(items.takeLast(MAX_QUEUE_SIZE))
    }

    @Synchronized
    fun peekAll(): List<QueuedViolation> = readAll()

    @Synchronized
    fun remove(id: String) {
        writeAll(readAll().filterNot { it.id == id })
    }

    @Synchronized
    fun count(): Int = readAll().size

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
    }
}

data class QueuedViolation(
    val id: String,
    val request: LockViolationRequest,
    val queuedAt: Long
)
