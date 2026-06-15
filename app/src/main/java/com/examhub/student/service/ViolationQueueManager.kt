package com.examhub.student.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.QueuedViolationEntity
import com.examhub.student.data.local.model.QueuedViolation
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.security.KeystoreCrypto
import com.examhub.student.worker.ViolationSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

class ViolationQueueManager(
    context: Context,
    private val gson: Gson,
    private val database: StudentAppDatabase,
    private val crypto: KeystoreCrypto
) {
    private val appContext = context.applicationContext
    private val dao = database.studentCacheDao()
    private val itemType = object : TypeToken<List<QueuedViolation>>() {}.type

    init {
        importLegacyPreferencesOnce()
    }

    fun enqueue(request: LockViolationRequest) {
        val id = request.clientEventId ?: UUID.randomUUID().toString()
        val item = QueuedViolation(
                id = id,
                request = request.copy(
                    clientEventId = id,
                    evidenceData = request.evidenceData + mapOf("client_event_id" to id)
                ),
                queuedAt = System.currentTimeMillis()
            )
        dbCall {
            database.runInTransaction {
                dao.upsertViolation(
                    QueuedViolationEntity(
                        id = item.id,
                        sessionId = item.request.sessionId,
                        encryptedJson = crypto.encryptString(gson.toJson(item)),
                        queuedAt = item.queuedAt
                    )
                )
                dao.trimViolations(MAX_QUEUE_SIZE)
            }
        }
        scheduleSync()
    }

    fun peekAll(): List<QueuedViolation> = dbCall {
        dao.getViolations().mapNotNull { entity ->
            runCatching {
                gson.fromJson(
                    crypto.decryptString(entity.encryptedJson),
                    QueuedViolation::class.java
                )
            }.getOrNull()
        }
    }

    fun remove(id: String) {
        dbCall { dao.deleteViolation(id) }
    }

    fun count(): Int = dbCall { dao.violationCount() }

    fun observeCount(): Flow<Int> = dao.observeViolationCount()

    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ViolationSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun importLegacyPreferencesOnce() {
        dbCall {
            if (dao.getMetadata(KEY_LEGACY_IMPORT_COMPLETE) == "1") return@dbCall
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_QUEUE, null).orEmpty()
            val items = if (raw.isBlank()) {
                emptyList()
            } else {
                runCatching {
                    gson.fromJson<List<QueuedViolation>>(raw, itemType)
                }.getOrDefault(emptyList())
            }
            database.runInTransaction {
                items.forEach { item ->
                    dao.upsertViolation(
                        QueuedViolationEntity(
                            id = item.id,
                            sessionId = item.request.sessionId,
                            encryptedJson = crypto.encryptString(gson.toJson(item)),
                            queuedAt = item.queuedAt
                        )
                    )
                }
                dao.trimViolations(MAX_QUEUE_SIZE)
                dao.upsertMetadata(
                    CacheMetadataEntity(
                        KEY_LEGACY_IMPORT_COMPLETE,
                        "1",
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    companion object {
        private const val PREFS_NAME = "lock_violation_queue"
        private const val KEY_QUEUE = "items"
        private const val KEY_LEGACY_IMPORT_COMPLETE = "legacy_violation_queue_import_complete"
        private const val MAX_QUEUE_SIZE = 200
        private const val WORK_NAME = "lock_violation_sync"
    }
}
