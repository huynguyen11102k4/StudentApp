package com.examhub.student.service

import android.content.Context
import androidx.work.WorkManager
import com.examhub.student.BuildConfig
import com.examhub.student.data.local.submission.QueuedSubmissionDao
import com.examhub.student.security.EncryptedSubmissionFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class BackendUrlManager(
    context: Context,
    private val tokenManager: TokenManager,
    private val offlineCacheManager: OfflineCacheManager,
    private val queuedSubmissionDao: QueuedSubmissionDao,
    private val submissionFileStore: EncryptedSubmissionFileStore
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun defaultBaseUrl(): String = BuildConfig.API_BASE_URL

    fun currentBaseUrl(): String = prefs.getString(KEY_BACKEND_URL, null)
        ?.normalizeBaseUrl()
        ?: defaultBaseUrl()

    fun overrideBaseUrl(): String? = prefs.getString(KEY_BACKEND_URL, null)?.normalizeBaseUrl()

    fun saveOverride(rawUrl: String): BackendUrlUpdateResult {
        val normalized = rawUrl.normalizeBaseUrl() ?: return BackendUrlUpdateResult.Invalid
        val previousBaseUrl = currentBaseUrl()
        if (normalized == defaultBaseUrl().normalizeBaseUrl()) {
            prefs.edit().remove(KEY_BACKEND_URL).apply()
            return if (previousBaseUrl == currentBaseUrl()) {
                BackendUrlUpdateResult.Unchanged
            } else {
                clearEnvironmentScopedState()
                BackendUrlUpdateResult.Changed
            }
        }
        prefs.edit().putString(KEY_BACKEND_URL, normalized).apply()
        return if (normalized == previousBaseUrl) {
            BackendUrlUpdateResult.Unchanged
        } else {
            clearEnvironmentScopedState()
            BackendUrlUpdateResult.Changed
        }
    }

    fun clearOverride(): BackendUrlUpdateResult {
        val previousBaseUrl = currentBaseUrl()
        if (overrideBaseUrl() == null) return BackendUrlUpdateResult.Unchanged
        prefs.edit().remove(KEY_BACKEND_URL).apply()
        return if (currentBaseUrl() == previousBaseUrl) {
            BackendUrlUpdateResult.Unchanged
        } else {
            clearEnvironmentScopedState()
            BackendUrlUpdateResult.Changed
        }
    }

    fun currentHttpUrl(): HttpUrl? = currentBaseUrl().toHttpUrlOrNull()

    private fun String.normalizeBaseUrl(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "http://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        val hasExplicitPort = rawAuthority(withScheme).substringAfterLast('@').contains(':')
        val base = if (
            parsed.scheme.equals("http", ignoreCase = true) &&
            parsed.port == 80 &&
            !hasExplicitPort
        ) {
            parsed.newBuilder().port(DEFAULT_DEV_PORT).build()
        } else {
            parsed
        }

        val pathSegments = base.encodedPathSegments.filter(String::isNotBlank)
        val builder = base.newBuilder()
        if (pathSegments.takeLast(2) != listOf("api", "v1")) {
            builder.addPathSegment("api")
            builder.addPathSegment("v1")
        }
        return builder.build().toString()
    }

    private fun rawAuthority(url: String): String {
        val withoutScheme = url.substringAfter("://", url)
        return withoutScheme.substringBefore('/')
    }

    private fun clearEnvironmentScopedState() {
        tokenManager.clearTokens()
        offlineCacheManager.clearUserScopedLists()
        clearQueuedOfflineSubmissions()
    }

    private fun clearQueuedOfflineSubmissions() {
        dbCall {
            val queuedIds = queuedSubmissionDao.getAll().map { it.clientSubmissionId }
            queuedSubmissionDao.deleteAll()
            submissionFileStore.deleteAll()
            runCatching {
                val workManager = WorkManager.getInstance(appContext)
                queuedIds.forEach { workManager.cancelUniqueWork("$SUBMISSION_SYNC_WORK_PREFIX$it") }
                workManager.cancelUniqueWork(SUBMISSION_SYNC_PENDING_WORK)
            }
        }
    }

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private companion object {
        const val PREFS_NAME = "backend_url"
        const val KEY_BACKEND_URL = "backend_url_override"
        const val DEFAULT_DEV_PORT = 3001
        const val SUBMISSION_SYNC_WORK_PREFIX = "submission_sync_"
        const val SUBMISSION_SYNC_PENDING_WORK = "submission_sync_pending"
    }
}

enum class BackendUrlUpdateResult {
    Changed,
    Unchanged,
    Invalid
}
