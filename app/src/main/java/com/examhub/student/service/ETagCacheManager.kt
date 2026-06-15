package com.examhub.student.service

import android.content.Context
import android.util.Base64
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.ETagCacheEntity
import com.examhub.student.data.local.model.CachedResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class ETagCacheManager(
    context: Context,
    private val database: StudentAppDatabase
) {
    private val appContext = context.applicationContext
    private val dao = database.studentCacheDao()

    init {
        importLegacyPreferencesOnce()
    }

    fun get(url: String): CachedResponse? {
        val cached = dbCall { dao.getEtag(key(url)) } ?: return null
        return CachedResponse(
            etag = cached.etag,
            body = cached.body,
            contentType = cached.contentType,
            code = cached.code,
            message = cached.message
        )
    }

    fun put(url: String, etag: String, body: String, contentType: String?, code: Int, message: String) {
        dbCall {
            dao.upsertEtag(
                ETagCacheEntity(
                    cacheKey = key(url),
                    url = url,
                    etag = etag,
                    body = body,
                    contentType = contentType,
                    code = code,
                    message = message,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun importLegacyPreferencesOnce() {
        dbCall {
            if (dao.getMetadata(KEY_LEGACY_IMPORT_COMPLETE) == "1") return@dbCall
            val all = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            val keys = all.keys.mapNotNull { raw ->
                raw.removeSuffix(".etag").takeIf { raw.endsWith(".etag") }
            }
            database.runInTransaction {
                keys.forEach { cacheKey ->
                    val etag = all["$cacheKey.etag"] as? String ?: return@forEach
                    val body = all["$cacheKey.body"] as? String ?: return@forEach
                    dao.upsertEtag(
                        ETagCacheEntity(
                            cacheKey = cacheKey,
                            url = "",
                            etag = etag,
                            body = body,
                            contentType = all["$cacheKey.contentType"] as? String,
                            code = all["$cacheKey.code"] as? Int ?: 200,
                            message = all["$cacheKey.message"] as? String ?: "OK",
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

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val PREFS_NAME = "etag_cache"
        const val KEY_LEGACY_IMPORT_COMPLETE = "legacy_etag_cache_import_complete"
    }
}
