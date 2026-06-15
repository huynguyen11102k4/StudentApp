package com.examhub.student.service

import android.content.Context
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.ActiveExamSessionEntity
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.model.ActiveExamSession
import com.examhub.student.security.KeystoreCrypto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ActiveExamSessionStore(
    context: Context,
    private val database: StudentAppDatabase,
    private val gson: Gson,
    private val crypto: KeystoreCrypto
) {
    private val appContext = context.applicationContext
    private val dao = database.studentCacheDao()

    init {
        importLegacyPreferencesOnce()
    }

    fun save(session: ActiveExamSession) {
        dbCall {
            dao.upsertActiveSession(
                ActiveExamSessionEntity(
                    examId = session.examId,
                    sessionId = session.sessionId,
                    encryptedJson = crypto.encryptString(gson.toJson(session)),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun get(examId: String): ActiveExamSession? {
        val session = getIncludingExpired(examId)
        if (session == null || !session.isStillActive()) {
            clear(examId)
            return null
        }
        return session
    }

    fun getIncludingExpired(examId: String): ActiveExamSession? {
        if (examId.isBlank()) return null
        val encrypted = dbCall { dao.getActiveSession(examId)?.encryptedJson } ?: return null
        return decode(encrypted)
    }

    fun clear(examId: String) {
        if (examId.isBlank()) return
        dbCall { dao.deleteActiveSession(examId) }
    }

    fun clearBySessionId(sessionId: String) {
        if (sessionId.isBlank()) return
        dbCall { dao.deleteActiveSessionBySessionId(sessionId) }
    }

    private fun importLegacyPreferencesOnce() {
        dbCall {
            if (dao.getMetadata(KEY_LEGACY_IMPORT_COMPLETE) == "1") return@dbCall
            val all = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            database.runInTransaction {
                all.entries
                    .filter { it.key.startsWith(KEY_PREFIX) && it.value is String }
                    .forEach { (_, value) ->
                        val raw = value as String
                        val session = runCatching {
                            gson.fromJson(raw, ActiveExamSession::class.java)
                        }.getOrNull() ?: return@forEach
                        dao.upsertActiveSession(
                            ActiveExamSessionEntity(
                                examId = session.examId,
                                sessionId = session.sessionId,
                                encryptedJson = crypto.encryptString(raw),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
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

    private fun decode(encrypted: String): ActiveExamSession? =
        runCatching {
            gson.fromJson(crypto.decryptString(encrypted), ActiveExamSession::class.java)
        }.getOrNull()

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val PREFS_NAME = "active_exam_sessions"
        const val KEY_PREFIX = "active_session_"
        const val KEY_LEGACY_IMPORT_COMPLETE = "legacy_active_sessions_import_complete"
    }
}
