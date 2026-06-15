package com.examhub.student.security

import android.content.Context
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SecurePermitStore(
    context: Context,
    private val crypto: KeystoreCrypto,
    private val database: StudentAppDatabase
) {
    private val appContext = context.applicationContext
    private val dao = database.studentCacheDao()

    init {
        importLegacyPreferencesOnce()
    }

    fun save(sessionId: String, permit: String) {
        if (sessionId.isBlank() || permit.isBlank()) return
        dbCall {
            dao.upsertMetadata(
                CacheMetadataEntity(
                    key = key(sessionId),
                    value = crypto.encryptString(permit),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun get(sessionId: String): String? {
        val encrypted = dbCall { dao.getMetadata(key(sessionId)) } ?: return null
        return runCatching { crypto.decryptString(encrypted) }.getOrNull()
    }

    fun clear(sessionId: String) {
        dbCall { dao.deleteMetadata(key(sessionId)) }
    }

    private fun importLegacyPreferencesOnce() {
        dbCall {
            if (dao.getMetadata(KEY_LEGACY_IMPORT_COMPLETE) == "1") return@dbCall
            val all = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            database.runInTransaction {
                all.entries
                    .filter { it.key.startsWith(KEY_PREFIX) && it.value is String }
                    .forEach { (key, value) ->
                        val legacyValue = value as String
                        val encryptedValue = runCatching {
                            crypto.decryptString(legacyValue)
                            legacyValue
                        }.getOrElse {
                            crypto.encryptString(legacyValue)
                        }
                        dao.upsertMetadata(
                            CacheMetadataEntity(
                                key = "$ROOM_PREFIX${key.removePrefix(KEY_PREFIX)}",
                                value = encryptedValue,
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

    private fun key(sessionId: String) = "$ROOM_PREFIX$sessionId"

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val PREFS_NAME = "secure_offline_permits"
        const val KEY_PREFIX = "permit_"
        const val ROOM_PREFIX = "offline_permit:"
        const val KEY_LEGACY_IMPORT_COMPLETE = "legacy_offline_permit_import_complete"
    }
}
