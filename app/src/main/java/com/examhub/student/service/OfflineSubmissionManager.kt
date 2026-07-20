package com.examhub.student.service

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.examhub.student.BuildConfig
import com.examhub.student.data.local.submission.QueuedSubmissionDao
import com.examhub.student.data.local.submission.QueuedSubmissionEntity
import com.examhub.student.data.local.model.FreezeResult
import com.examhub.student.data.local.model.SubmissionSyncStatus
import com.examhub.student.data.local.model.WorkerSyncResult
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.submission.PresignSubmissionImageRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.model.response.submission.StudentSubmitResponse
import com.examhub.student.repository.StudentSubmissionRepository
import com.examhub.student.security.EncryptedSubmissionFileStore
import com.examhub.student.security.KeystoreCrypto
import com.examhub.student.security.SecurePermitStore
import com.examhub.student.worker.SubmissionSyncWorker
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class OfflineSubmissionManager(
    context: Context,
    private val dao: QueuedSubmissionDao,
    private val repository: StudentSubmissionRepository,
    private val permitStore: SecurePermitStore,
    private val fileStore: EncryptedSubmissionFileStore,
    private val crypto: KeystoreCrypto,
    private val gson: Gson,
    private val tokenManager: TokenManager,
    private val activeSessionStore: ActiveExamSessionStore
) {
    private val appContext = context.applicationContext
    private val syncMutex = Mutex()

    suspend fun freezeAndSync(
        sessionId: String,
        examId: String,
        requestFactory: (clientSubmissionId: String, capturedAt: String) -> StudentSubmitRequest,
        rawImage: ByteArray? = null,
        dewarpedImage: ByteArray? = null,
        processedImage: ByteArray? = null
    ): FreezeResult {
        val clientSubmissionId = UUID.randomUUID().toString()
        val capturedAt = nowIso()
        val request = requestFactory(clientSubmissionId, capturedAt)
            .withFrozenImageHashes(rawImage, dewarpedImage, processedImage)
            .copy(offlinePermit = null, payloadSha256 = null)
        logSubmitPayload(
            message = "Submission freeze payload sessionId=$sessionId examId=$examId clientSubmissionId=$clientSubmissionId " +
                "rawBytes=${rawImage?.size ?: 0} dewarpedBytes=${dewarpedImage?.size ?: 0} processedBytes=${processedImage?.size ?: 0}",
            request = request
        )
        val now = System.currentTimeMillis()
        val entity = QueuedSubmissionEntity(
            clientSubmissionId = clientSubmissionId,
            sessionId = sessionId,
            examId = examId,
            deviceId = activeSessionStore.getIncludingExpired(examId)
                ?.deviceId
                ?.takeIf { it.isNotBlank() }
                ?: tokenManager.getDeviceId(),
            capturedAt = capturedAt,
            encryptedPayload = crypto.encryptString(gson.toJson(request.copy(offlinePermit = null))),
            rawImagePath = rawImage?.let { fileStore.write(clientSubmissionId, "raw", it) },
            dewarpedImagePath = dewarpedImage?.let { fileStore.write(clientSubmissionId, "dewarped", it) },
            processedImagePath = processedImage?.let { fileStore.write(clientSubmissionId, "processed", it) },
            status = SubmissionSyncStatus.PENDING_SYNC.name,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        dao.insert(entity)

        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            scheduleSync(clientSubmissionId)
            return FreezeResult.Pending(clientSubmissionId)
        }

        val immediate = runCatching {
            syncSafely(entity, useOfflinePermit = true)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            dao.updateStatus(
                clientSubmissionId,
                SubmissionSyncStatus.PENDING_SYNC.name,
                System.currentTimeMillis(),
                "NETWORK_ERROR",
                error.message
            )
            SyncAttempt.Retry
        }
        return when (immediate) {
            is SyncAttempt.Synced -> FreezeResult.Synced(clientSubmissionId, immediate.response)
            is SyncAttempt.TerminalFailure -> FreezeResult.TerminalFailure(clientSubmissionId, immediate.code)
            SyncAttempt.Retry, SyncAttempt.Skipped -> {
                dao.updateStatus(clientSubmissionId, SubmissionSyncStatus.PENDING_SYNC.name, System.currentTimeMillis())
                scheduleSync(clientSubmissionId)
                FreezeResult.Pending(clientSubmissionId)
            }
        }
    }

    suspend fun syncPending(clientSubmissionId: String? = null): WorkerSyncResult {
        val items = clientSubmissionId?.let { listOfNotNull(dao.get(it)) } ?: dao.getPending()
        var needsRetry = false
        var hasFailure = false
        items.forEach { item ->
            val attempt = runCatching {
                syncSafely(item, useOfflinePermit = true)
            }.getOrElse { error ->
                dao.updateStatus(
                    item.clientSubmissionId,
                    SubmissionSyncStatus.PENDING_SYNC.name,
                    System.currentTimeMillis(),
                    "LOCAL_SYNC_ERROR",
                    error.message
                )
                SyncAttempt.Retry
            }
            when (attempt) {
                SyncAttempt.Retry -> needsRetry = true
                is SyncAttempt.TerminalFailure -> hasFailure = true
                else -> Unit
            }
        }
        return when {
            needsRetry -> WorkerSyncResult.RETRY
            hasFailure -> WorkerSyncResult.FAILURE
            else -> WorkerSyncResult.SUCCESS
        }
    }

    fun observe(clientSubmissionId: String): Flow<QueuedSubmissionEntity?> = dao.observe(clientSubmissionId)

    fun scheduleSync(clientSubmissionId: String) {
        val request = OneTimeWorkRequestBuilder<SubmissionSyncWorker>()
            .setInputData(workDataOf(SubmissionSyncWorker.KEY_CLIENT_SUBMISSION_ID to clientSubmissionId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "submission_sync_$clientSubmissionId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun schedulePendingSync() {
        val request = OneTimeWorkRequestBuilder<SubmissionSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "submission_sync_pending",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun syncSafely(
        item: QueuedSubmissionEntity,
        useOfflinePermit: Boolean
    ): SyncAttempt = syncMutex.withLock {
        val latest = dao.get(item.clientSubmissionId) ?: return@withLock SyncAttempt.Retry
        if (latest.status == SubmissionSyncStatus.SYNCED.name ||
            latest.status == SubmissionSyncStatus.FAILED_CAPTURE_AFTER_DEADLINE.name ||
            latest.isNonRetryableTerminalFailure()
        ) {
            return@withLock SyncAttempt.Skipped
        }
        sync(latest, useOfflinePermit)
    }

    private suspend fun sync(item: QueuedSubmissionEntity, useOfflinePermit: Boolean): SyncAttempt {
        val storedRequest = runCatching {
            gson.fromJson(crypto.decryptString(item.encryptedPayload), StudentSubmitRequest::class.java)
        }.getOrElse {
            markTerminal(item, "LOCAL_PAYLOAD_INVALID", it.message)
            return SyncAttempt.TerminalFailure("LOCAL_PAYLOAD_INVALID")
        }
        val hadBlankAnswers = storedRequest.hasBlankAnswers()
        var request = storedRequest.sanitizeFrozenPayload()

        var rawUrl = request.rawImageUrl
        var dewarpedUrl = request.dewarpedImageUrl
        var processedUrl = request.processedImageUrl
        fun currentUrl(kind: String): String? = when (kind) {
            "raw" -> rawUrl
            "dewarped" -> dewarpedUrl
            "processed" -> processedUrl
            else -> null
        }
        val images = listOf(
            Triple("raw", item.rawImagePath, { value: String -> rawUrl = value }),
            Triple("dewarped", item.dewarpedImagePath, { value: String -> dewarpedUrl = value }),
            Triple("processed", item.processedImagePath, { value: String -> processedUrl = value })
        )
        if (images.any { (kind, path, _) -> path != null && currentUrl(kind).isNullOrBlank() }) {
            dao.updateStatus(item.clientSubmissionId, SubmissionSyncStatus.UPLOADING_IMAGES.name, System.currentTimeMillis())
        }
        for ((kind, path, assignUrl) in images) {
            if (path == null) continue
            if (!currentUrl(kind).isNullOrBlank()) continue
            val bytes = fileStore.read(path) ?: run {
                markTerminal(item, "LOCAL_IMAGE_INVALID", kind)
                return SyncAttempt.TerminalFailure("LOCAL_IMAGE_INVALID")
            }
            val presignRequest = PresignSubmissionImageRequest(bytes.size.toLong(), "image/jpeg", kind)
            logLong(
                "Submission presign request sessionId=${item.sessionId} clientSubmissionId=${item.clientSubmissionId} " +
                    "deviceId=${item.deviceId} imageKind=$kind",
                gson.toJson(presignRequest)
            )
            val presign = repository.presignImage(
                item.sessionId,
                presignRequest,
                item.deviceId
            ).first { it !is ApiResult.Loading }
            if (presign is ApiResult.Error) {
                logSyncError(
                    message = "Submission presign failed sessionId=${item.sessionId} " +
                        "clientSubmissionId=${item.clientSubmissionId} imageKind=$kind",
                    code = presign.exception.code,
                    errorMessage = presign.exception.message,
                    httpCode = presign.exception.httpCode,
                    details = presign.exception.details
                )
                return classifyFailure(
                    item,
                    presign.exception.code,
                    presign.exception.message,
                    presign.exception.httpCode
                )
            }
            presign as ApiResult.Success
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Submission presign response sessionId=${item.sessionId} clientSubmissionId=${item.clientSubmissionId} " +
                        "imageKind=$kind fileUrl=${presign.data.fileUrl}"
                )
            }
            val upload = repository.uploadImage(presign.data.uploadUrl, bytes, "image/jpeg")
                .first { it !is ApiResult.Loading }
            if (upload is ApiResult.Error) {
                logSyncError(
                    message = "Submission image upload failed sessionId=${item.sessionId} " +
                        "clientSubmissionId=${item.clientSubmissionId} imageKind=$kind",
                    code = upload.exception.code,
                    errorMessage = upload.exception.message,
                    httpCode = upload.exception.httpCode,
                    details = upload.exception.details
                )
                return classifyFailure(
                    item,
                    upload.exception.code,
                    upload.exception.message,
                    upload.exception.httpCode
                )
            }
            assignUrl(presign.data.fileUrl)
        }
        if (request.payloadSha256.isNullOrBlank() || hadBlankAnswers) {
            request = request.withFrozenImageHashes(
                rawImage = item.rawImagePath?.let(fileStore::read),
                dewarpedImage = item.dewarpedImagePath?.let(fileStore::read),
                processedImage = item.processedImagePath?.let(fileStore::read)
            )
        }

        dao.updateStatus(item.clientSubmissionId, SubmissionSyncStatus.SYNCING.name, System.currentTimeMillis())
        val permit = if (useOfflinePermit) permitStore.get(item.sessionId) else null
        val finalPayload = request.copy(
            offlinePermit = null,
            payloadSha256 = null,
            rawImageUrl = rawUrl,
            dewarpedImageUrl = dewarpedUrl,
            processedImageUrl = processedUrl
        )
        dao.updateEncryptedPayload(
            id = item.clientSubmissionId,
            encryptedPayload = crypto.encryptString(gson.toJson(finalPayload)),
            updatedAt = System.currentTimeMillis()
        )
        val submitRequest = finalPayload.copy(
            offlinePermit = permit,
            payloadSha256 = hashSubmissionPayload(finalPayload)
        )
        logSubmitPayload(
            message = "Submission submit request POST student/sessions/${item.sessionId}/submit " +
                "clientSubmissionId=${item.clientSubmissionId} deviceId=${item.deviceId} useOfflinePermit=$useOfflinePermit",
            request = submitRequest
        )
        val submit = repository.submit(
            item.sessionId,
            submitRequest,
            item.deviceId
        ).first { it !is ApiResult.Loading }

        return when (submit) {
            is ApiResult.Success -> {
                dao.markSynced(
                    id = item.clientSubmissionId,
                    updatedAt = System.currentTimeMillis(),
                    submissionId = submit.data.submissionId.takeIf(String::isNotBlank),
                    resultId = submit.data.resultId?.takeIf(String::isNotBlank),
                    serverStatus = submit.data.status.takeIf(String::isNotBlank)
                        ?: submit.data.sessionStatus.takeIf(String::isNotBlank)
                )
                fileStore.deleteSubmission(item.clientSubmissionId)
                permitStore.clear(item.sessionId)
                activeSessionStore.clearBySessionId(item.sessionId)
                SyncAttempt.Synced(submit.data)
            }
            is ApiResult.Error -> {
                logSyncError(
                    message = "Submission submit failed sessionId=${item.sessionId} " +
                        "clientSubmissionId=${item.clientSubmissionId}",
                    code = submit.exception.code,
                    errorMessage = submit.exception.message,
                    httpCode = submit.exception.httpCode,
                    details = submit.exception.details
                )
                classifyFailure(
                    item,
                    submit.exception.code,
                    submit.exception.message,
                    submit.exception.httpCode
                )
            }
            ApiResult.Loading -> SyncAttempt.Retry
        }
    }

    private suspend fun classifyFailure(
        item: QueuedSubmissionEntity,
        code: String,
        message: String?,
        httpCode: Int?
    ): SyncAttempt {
        val normalizedCode = normalizeErrorCode(code)
        if (normalizedCode in TERMINAL_CODES) {
            markTerminal(item, normalizedCode, message)
            return SyncAttempt.TerminalFailure(normalizedCode)
        }
        return if (
            normalizedCode in RETRYABLE_CODES ||
            httpCode == null ||
            httpCode in RETRYABLE_HTTP_CODES ||
            httpCode >= 500
        ) {
            dao.updateStatus(
                item.clientSubmissionId,
                SubmissionSyncStatus.PENDING_SYNC.name,
                System.currentTimeMillis(),
                normalizedCode,
                message
            )
            SyncAttempt.Retry
        } else {
            markTerminal(item, normalizedCode, message)
            SyncAttempt.TerminalFailure(normalizedCode)
        }
    }

    private suspend fun markTerminal(item: QueuedSubmissionEntity, code: String, message: String?) {
        val status = if (code == "OFFLINE_CAPTURE_AFTER_DEADLINE") {
            SubmissionSyncStatus.FAILED_CAPTURE_AFTER_DEADLINE
        } else {
            SubmissionSyncStatus.FAILED_TERMINAL
        }
        dao.updateStatus(item.clientSubmissionId, status.name, System.currentTimeMillis(), code, message)
    }

    private fun nowIso(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())
    }

    private fun logSubmitPayload(message: String, request: StudentSubmitRequest) {
        val redacted = request.copy(
            offlinePermit = request.offlinePermit?.let { "<redacted:${it.length}>" }
        )
        logLong(message, gson.toJson(redacted))
    }

    private fun logLong(message: String, payload: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, message)
        payload.chunked(LOG_CHUNK_SIZE).forEachIndexed { index, chunk ->
            Log.d(TAG, "$message chunk=${index + 1}: $chunk")
        }
    }

    private fun logSyncError(
        message: String,
        code: String,
        errorMessage: String?,
        httpCode: Int?,
        details: List<String>
    ) {
        if (!BuildConfig.DEBUG) return
        val detailText = details.takeIf { it.isNotEmpty() }?.joinToString(separator = " | ").orEmpty()
        Log.d(
            TAG,
            "$message httpCode=${httpCode ?: "null"} code=$code " +
                "errorMessage=${errorMessage.orEmpty()} details=$detailText"
            )
    }

    private fun hashSubmissionPayload(request: StudentSubmitRequest): String =
        stableSubmissionPayload(request).let { canonical ->
            val hash = sha256Hex(canonical)
            logLong("Submission payload hash input sha256=$hash", canonical)
            logHashVariant(
                "Submission payload hash variant without_image_hash_feedback",
                stableSubmissionPayload(request.withoutImageHashFeedback())
            )
            logHashVariant(
                "Submission payload hash variant gson_body_order",
                gson.toJson(request.copy(offlinePermit = null, payloadSha256 = null))
            )
            hash
        }

    private fun logHashVariant(label: String, payload: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "$label sha256=${sha256Hex(payload)}")
    }

    private fun stableSubmissionPayload(request: StudentSubmitRequest): String =
        stableStringify(
            sortedMapOf(
                "client_submission_id" to stableValue(request.clientSubmissionId),
                "raw_image_url" to stableValue(request.rawImageUrl),
                "dewarped_image_url" to stableValue(request.dewarpedImageUrl),
                "processed_image_url" to stableValue(request.processedImageUrl),
                "student_id" to stableValue(request.scannedStudentId),
                "class_code" to stableValue(request.scannedClassCode),
                "exam_code" to stableValue(request.scannedExamCode),
                "id_result" to stableIdResult(request.idResult),
                "student_answers" to request.studentAnswers.map { answer ->
                    sortedMapOf(
                        "question_number" to stableValue(answer.questionNumber),
                        "answer" to stableValue(answer.answer)
                    )
                },
                "captured_at" to stableValue(request.capturedAt),
                "image_quality_score" to stableValue(request.imageQualityScore),
                "quality_feedback" to stableQualityFeedback(request.qualityFeedback)
            )
        )

    private fun stableIdResult(
        idResult: com.examhub.student.model.request.submission.IdZoneResultRequest?
    ): Any? {
        idResult ?: return null
        return sortedMapOf(
            "student_id" to stableValue(idResult.studentId),
            "class_code" to stableValue(idResult.classCode),
            "exam_code" to stableValue(idResult.examCode),
            "id_ok" to stableValue(idResult.idOk),
            "id_error" to (idResult.idError?.let(::stableValue) ?: StableUndefined)
        )
    }

    private fun stableQualityFeedback(values: Map<String, String>?): Any? =
        values?.toSortedMap()?.mapValues { stableValue(it.value) }

    private fun stableValue(value: String?): Any? = value

    private fun stableValue(value: Int?): Any? = value

    private fun stableValue(value: Boolean?): Any? = value

    private fun stableStringify(value: Any?): String =
        when (value) {
            null -> "null"
            StableUndefined -> "undefined"
            is String -> gson.toJson(value)
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries
                .map { it.key.toString() to it.value }
                .sortedWith { left, right -> left.first.compareTo(right.first) }
                .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, item) ->
                    "${gson.toJson(key)}:${stableStringify(item)}"
                }
            is Iterable<*> -> value.joinToString(separator = ",", prefix = "[", postfix = "]") {
                stableStringify(it)
            }
            else -> gson.toJson(value)
        }

    private fun canonicalSubmissionPayload(request: StudentSubmitRequest): JsonObject =
        sortedJsonObject(
            "captured_at" to jsonValue(request.capturedAt),
            "class_code" to jsonValue(request.scannedClassCode),
            "client_submission_id" to jsonValue(request.clientSubmissionId),
            "dewarped_image_url" to jsonValue(request.dewarpedImageUrl),
            "exam_code" to jsonValue(request.scannedExamCode),
            "id_result" to canonicalIdResult(request.idResult),
            "image_quality_score" to jsonValue(request.imageQualityScore),
            "processed_image_url" to jsonValue(request.processedImageUrl),
            "quality_feedback" to canonicalStringMap(request.qualityFeedback),
            "raw_image_url" to jsonValue(request.rawImageUrl),
            "student_answers" to canonicalAnswers(request.studentAnswers),
            "student_id" to jsonValue(request.scannedStudentId)
        )

    private fun canonicalIdResult(idResult: com.examhub.student.model.request.submission.IdZoneResultRequest?): JsonElement =
        idResult?.let {
            sortedJsonObject(
                "class_code" to jsonValue(it.classCode),
                "exam_code" to jsonValue(it.examCode),
                "id_error" to jsonValue(it.idError),
                "id_ok" to jsonValue(it.idOk),
                "student_id" to jsonValue(it.studentId)
            )
        } ?: JsonNull.INSTANCE

    private fun canonicalAnswers(
        answers: List<com.examhub.student.model.request.submission.StudentAnswerRequest>
    ): JsonArray =
        JsonArray().apply {
            answers.forEach { answer ->
                add(
                    sortedJsonObject(
                        "answer" to jsonValue(answer.answer),
                        "question_number" to jsonValue(answer.questionNumber)
                    )
                )
            }
        }

    private fun canonicalStringMap(values: Map<String, String>?): JsonElement =
        values?.entries
            ?.sortedBy { it.key }
            ?.fold(JsonObject()) { obj, entry ->
                obj.apply { add(entry.key, jsonValue(entry.value)) }
            }
            ?: JsonNull.INSTANCE

    private fun sortedJsonObject(vararg entries: Pair<String, JsonElement>): JsonObject =
        entries
            .filterNot { it.second is JsonNull }
            .sortedBy { it.first }
            .fold(JsonObject()) { obj, (key, value) ->
                obj.apply { add(key, value) }
            }

    private fun jsonValue(value: String?): JsonElement =
        value?.let(::JsonPrimitive) ?: JsonNull.INSTANCE

    private fun jsonValue(value: Int?): JsonElement =
        value?.let(::JsonPrimitive) ?: JsonNull.INSTANCE

    private fun jsonValue(value: Boolean?): JsonElement =
        value?.let(::JsonPrimitive) ?: JsonNull.INSTANCE

    private object StableUndefined

    private fun StudentSubmitRequest.withFrozenImageHashes(
        rawImage: ByteArray?,
        dewarpedImage: ByteArray?,
        processedImage: ByteArray?
    ): StudentSubmitRequest {
        val hashes = buildMap {
            rawImage?.let { put("raw_image_sha256", sha256Hex(it)) }
            dewarpedImage?.let { put("dewarped_image_sha256", sha256Hex(it)) }
            processedImage?.let { put("processed_image_sha256", sha256Hex(it)) }
        }
        if (hashes.isEmpty()) return this
        return copy(qualityFeedback = (qualityFeedback.orEmpty() + hashes))
    }

    private fun StudentSubmitRequest.withoutImageHashFeedback(): StudentSubmitRequest =
        copy(
            qualityFeedback = qualityFeedback
                ?.filterKeys { key -> !key.endsWith("_image_sha256") }
                ?.takeIf { it.isNotEmpty() }
        )

    private fun StudentSubmitRequest.sanitizeFrozenPayload(): StudentSubmitRequest =
        copy(
            studentAnswers = studentAnswers.mapNotNull { answer ->
                val normalizedAnswer = answer.answer?.trim()?.takeIf(String::isNotBlank)
                normalizedAnswer?.let {
                    answer.copy(answer = it)
                }
            }
        )

    private fun StudentSubmitRequest.hasBlankAnswers(): Boolean =
        studentAnswers.any { it.answer.isNullOrBlank() }

    private fun sha256Hex(value: String): String =
        sha256Hex(value.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun QueuedSubmissionEntity.isNonRetryableTerminalFailure(): Boolean {
        if (status != SubmissionSyncStatus.FAILED_TERMINAL.name) return false
        val normalized = normalizeErrorCode(lastErrorCode.orEmpty())
        return normalized in TERMINAL_CODES ||
            normalized == "LOCAL_PAYLOAD_INVALID" ||
            normalized == "LOCAL_IMAGE_INVALID"
    }

    private fun normalizeErrorCode(code: String): String {
        val normalized = code.trim().uppercase()
            .replace('-', '_')
            .replace(' ', '_')
        return when {
            normalized in setOf(
                "OFFLINE_PERMIT_INVALID",
                "OFFLINE_PERMIT_EXPIRED",
                "PERMIT_INVALID",
                "PERMIT_EXPIRED"
            ) ->
                "INVALID_OFFLINE_PERMIT"
            normalized == "PERMIT_MISMATCH" ||
                normalized == "OFFLINE_SUBMISSION_PERMIT_MISMATCH" ||
                normalized.contains("PERMIT_MISMATCH") ->
                "OFFLINE_PERMIT_MISMATCH"
            normalized == "DEVICE_MISMATCH" ||
                normalized == "SUBMISSION_FROM_DIFFERENT_DEVICE" ||
                normalized.contains("DEVICE_MISMATCH") ->
                "SUBMISSION_DEVICE_MISMATCH"
            normalized == "MISSING_CLIENT_SUBMISSION_ID" ||
                normalized == "CLIENT_ID_REQUIRED" ->
                "CLIENT_SUBMISSION_ID_REQUIRED"
            normalized == "CLIENT_ID_CONFLICT" ||
                normalized == "DUPLICATE_CLIENT_SUBMISSION_ID" ->
                "CLIENT_SUBMISSION_ID_CONFLICT"
            else -> normalized
        }
    }

    private sealed interface SyncAttempt {
        data class Synced(val response: StudentSubmitResponse) : SyncAttempt
        data class TerminalFailure(val code: String) : SyncAttempt
        data object Retry : SyncAttempt
        data object Skipped : SyncAttempt
    }

    companion object {
        private const val TAG = "OfflineSubmission"
        private const val LOG_CHUNK_SIZE = 3500

        private val TERMINAL_CODES = setOf(
            "INVALID_OFFLINE_PERMIT",
            "OFFLINE_PERMIT_MISMATCH",
            "OFFLINE_CAPTURE_AFTER_DEADLINE",
            "SUBMISSION_DEVICE_MISMATCH",
            "CLIENT_SUBMISSION_ID_REQUIRED",
            "CLIENT_SUBMISSION_ID_CONFLICT"
        )
        private val RETRYABLE_CODES = setOf(
            "NETWORK_ERROR",
            "UPLOAD_URL_EXPIRED",
            "UPLOAD_RETRYABLE",
            "RATE_LIMITED",
            "TOO_MANY_REQUESTS",
            "REQUEST_TIMEOUT"
        )
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429)
    }
}
