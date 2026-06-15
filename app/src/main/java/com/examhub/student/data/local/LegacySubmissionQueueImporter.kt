package com.examhub.student.data.local

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.submission.QueuedSubmissionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

class LegacySubmissionQueueImporter(
    context: Context,
    private val database: StudentAppDatabase
) {
    private val appContext = context.applicationContext
    private val cacheDao = database.studentCacheDao()
    private val submissionDao = database.queuedSubmissionDao()

    fun importOnce() {
        dbCall {
            if (cacheDao.getMetadata(KEY_IMPORT_COMPLETE) == "1") return@dbCall
            val legacyFile = appContext.getDatabasePath(LEGACY_DATABASE_NAME)
            val items = readLegacyItems(legacyFile).getOrElse {
                return@dbCall
            }
            database.runInTransaction {
                items.forEach { submissionDao.insertOrIgnore(it) }
                cacheDao.upsertMetadata(
                    CacheMetadataEntity(
                        key = KEY_IMPORT_COMPLETE,
                        value = "1",
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun readLegacyItems(file: File): Result<List<QueuedSubmissionEntity>> {
        if (!file.exists()) return Result.success(emptyList())
        val legacy = try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (error: Exception) {
            return Result.failure(error)
        }
        return try {
            Result.success(legacy.rawQuery("SELECT * FROM queued_submissions", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toQueuedSubmission())
                }
            })
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            legacy.close()
        }
    }

    private fun Cursor.toQueuedSubmission() = QueuedSubmissionEntity(
        clientSubmissionId = string("clientSubmissionId"),
        sessionId = string("sessionId"),
        examId = string("examId"),
        deviceId = string("deviceId"),
        capturedAt = string("capturedAt"),
        encryptedPayload = string("encryptedPayload"),
        rawImagePath = nullableString("rawImagePath"),
        dewarpedImagePath = nullableString("dewarpedImagePath"),
        processedImagePath = nullableString("processedImagePath"),
        status = string("status"),
        createdAtMillis = long("createdAtMillis"),
        updatedAtMillis = long("updatedAtMillis"),
        lastErrorCode = nullableString("lastErrorCode"),
        lastErrorMessage = nullableString("lastErrorMessage")
    )

    private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))
    private fun Cursor.nullableString(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }
    private fun Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val LEGACY_DATABASE_NAME = "student_submission_queue.db"
        const val KEY_IMPORT_COMPLETE = "legacy_submission_queue_import_complete"
    }
}
