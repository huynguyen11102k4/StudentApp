package com.examhub.student.service

import android.content.Context
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.JsonCacheEntity
import com.examhub.student.data.local.entity.OfflineExamEntity
import com.examhub.student.data.local.entity.StudentIdentityEntity
import com.examhub.student.data.model.AppNotification
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.response.profile.UserResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class OfflineCacheManager(
    context: Context,
    private val database: StudentAppDatabase,
    private val gson: Gson
) {
    private val appContext = context.applicationContext
    private val dao = database.studentCacheDao()
    private val mutationLock = Any()

    init {
        importLegacyPreferencesOnce()
    }

    fun saveTemplate(examId: String, templateJson: String) =
        updateOfflineExam(examId) { it.copy(templateJson = templateJson) }

    fun getTemplate(examId: String): String? = dbCall { dao.getOfflineExam(examId)?.templateJson }

    fun saveQuestionMetadata(examId: String, questionMetadataJson: String) =
        updateOfflineExam(examId) { it.copy(questionMetadataJson = questionMetadataJson) }

    fun getQuestionMetadata(examId: String): String? =
        dbCall { dao.getOfflineExam(examId)?.questionMetadataJson }

    fun saveExamDetail(examId: String, detailJson: String) =
        updateOfflineExam(examId) { it.copy(detailJson = detailJson) }

    fun getExamDetail(examId: String): String? =
        dbCall { dao.getOfflineExam(examId)?.detailJson }

    fun saveExamClassCode(examId: String, classCode: String?) {
        if (classCode.isNullOrBlank()) return
        updateOfflineExam(examId) { it.copy(classCode = classCode) }
    }

    fun getExamClassCode(examId: String): String? =
        dbCall { dao.getOfflineExam(examId)?.classCode }

    fun isOfflineReady(examId: String): Boolean =
        dbCall {
            val exam = dao.getOfflineExam(examId)
            exam?.templateJson != null && exam.questionMetadataJson != null
        }

    fun markOfflineReady(examId: String) =
        updateOfflineExam(examId) { it.copy(offlineMarked = true) }

    fun saveExamBasics(exams: List<Exam>) {
        synchronized(mutationLock) {
            dbCall {
                database.runInTransaction {
                    val previous = readJsonNamespace<Exam>(NAMESPACE_EXAMS).associateBy { it.id }
                    dao.deleteJsonNamespace(NAMESPACE_EXAMS)
                    dao.upsertJson(exams.mapIndexed { index, exam ->
                        val normalized = exam.copy(
                            className = exam.className.ifBlank { previous[exam.id]?.className.orEmpty() },
                            isOfflineReady = isOfflineReadyInDao(exam.id)
                        )
                        jsonEntity(NAMESPACE_EXAMS, exam.id, gson.toJson(normalized), index.toLong())
                    })
                }
            }
        }
    }

    fun saveExamBasic(exam: Exam) {
        synchronized(mutationLock) {
            dbCall {
                database.runInTransaction {
                    val previous = dao.getJson(NAMESPACE_EXAMS, exam.id)?.json?.let { parseJson<Exam>(it) }
                    val normalized = exam.copy(
                        className = exam.className.ifBlank { previous?.className.orEmpty() },
                        isOfflineReady = isOfflineReadyInDao(exam.id)
                    )
                    dao.upsertJson(jsonEntity(NAMESPACE_EXAMS, exam.id, gson.toJson(normalized), now()))
                }
            }
        }
    }

    fun getCachedExamBasics(): List<Exam> = dbCall {
        readJsonNamespace<Exam>(NAMESPACE_EXAMS).map {
            it.copy(isOfflineReady = isOfflineReadyInDao(it.id))
        }
    }

    fun getCachedExamBasic(examId: String): Exam? = dbCall {
        dao.getJson(NAMESPACE_EXAMS, examId)?.json?.let { parseJson<Exam>(it) }
            ?.copy(isOfflineReady = isOfflineReadyInDao(examId))
    }

    fun getOfflineReadyExamBasics(): List<Exam> {
        val offlineIds = getOfflineExamIds().toSet()
        return getCachedExamBasics()
            .filter { it.id in offlineIds || it.isOfflineReady }
            .map { it.copy(isOfflineReady = true) }
    }

    fun saveClassBasics(classes: List<SchoolClass>) {
        if (classes.isEmpty()) return
        synchronized(mutationLock) {
            dbCall {
                database.runInTransaction {
                    val merged = readJsonNamespace<SchoolClass>(NAMESPACE_CLASSES)
                        .associateBy { it.id }
                        .toMutableMap()
                    classes.forEach { merged[it.id] = it }
                    dao.deleteJsonNamespace(NAMESPACE_CLASSES)
                    dao.upsertJson(merged.values.mapIndexed { index, item ->
                        jsonEntity(NAMESPACE_CLASSES, item.id, gson.toJson(item), index.toLong())
                    })
                }
            }
        }
    }

    fun getCachedClassBasics(): List<SchoolClass> =
        dbCall { readJsonNamespace(NAMESPACE_CLASSES) }

    fun saveNotifications(notifications: List<AppNotification>) {
        val dismissed = getDismissedNotificationIds().toSet()
        synchronized(mutationLock) {
            dbCall {
                database.runInTransaction {
                    dao.deleteJsonNamespace(NAMESPACE_NOTIFICATIONS)
                    dao.upsertJson(notifications.filterNot { it.id in dismissed }.mapIndexed { index, item ->
                        jsonEntity(NAMESPACE_NOTIFICATIONS, item.id, gson.toJson(item), index.toLong())
                    })
                }
            }
        }
    }

    fun getCachedNotifications(): List<AppNotification> {
        val dismissed = getDismissedNotificationIds().toSet()
        return dbCall {
            readJsonNamespace<AppNotification>(NAMESPACE_NOTIFICATIONS)
                .filterNot { it.id in dismissed }
        }
    }

    fun clearNotifications() = dbCall { dao.deleteJsonNamespace(NAMESPACE_NOTIFICATIONS) }

    fun dismissNotifications(notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        val merged = getDismissedNotificationIds().toMutableSet()
        merged.addAll(notificationIds.filter(String::isNotBlank))
        saveMetadata(KEY_DISMISSED_NOTIFICATION_IDS, gson.toJson(merged.toList()))
        clearNotifications()
    }

    fun getDismissedNotificationIds(): List<String> = dbCall {
        dao.getMetadata(KEY_DISMISSED_NOTIFICATION_IDS)?.let {
            parseJson(it, emptyList())
        } ?: emptyList()
    }

    fun saveStudentIdentity(user: UserResponse) {
        val stableId = listOf(user.student?.id, user.id)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?.uppercase()
            ?: return
        dbCall {
            dao.upsertStudentIdentity(
                StudentIdentityEntity(
                    stableId = stableId,
                    internalCode = user.student?.internalId?.normalizeIdentity(),
                    externalCode = user.student?.studentCode?.normalizeIdentity(),
                    fullName = user.fullName,
                    updatedAt = now()
                )
            )
        }
    }

    fun stableStudentId(codeOrId: String?): String? {
        val normalized = codeOrId.normalizeIdentity()
        if (normalized.isBlank()) return null
        return dbCall { dao.findStudentIdentity(normalized)?.stableId } ?: normalized
    }

    fun getOfflineExamIds(): List<String> =
        dbCall { dao.getOfflineExams().filter { it.offlineMarked }.map { it.examId } }

    fun removeOfflineData(examId: String): Boolean = dbCall {
        if (examId in dao.getActiveSessionExamIds()) {
            false
        } else {
            dao.deleteOfflineExam(examId)
            true
        }
    }

    fun clearUserScopedLists() {
        dbCall {
            database.runInTransaction {
                dao.deleteJsonNamespace(NAMESPACE_EXAMS)
                dao.deleteJsonNamespace(NAMESPACE_CLASSES)
                dao.deleteJsonNamespace(NAMESPACE_NOTIFICATIONS)
                dao.deleteJsonNamespace(NAMESPACE_AUTH_CACHE)
                dao.deleteAllOfflineExams()
                dao.deleteAllActiveSessions()
                dao.deleteAllEtags()
                dao.deleteAllStudentIdentities()
                saveMetadataInDao(KEY_DISMISSED_NOTIFICATION_IDS, "[]")
            }
        }
    }

    fun clearOfflineDownloads(): Int {
        val removedIds = dbCall {
            val activeIds = dao.getActiveSessionExamIds().toSet()
            val removableIds = dao.getOfflineExams()
                .filter { it.offlineMarked && it.examId !in activeIds }
                .map { it.examId }
            if (removableIds.isNotEmpty()) {
                dao.deleteOfflineExams(removableIds)
            }
            removableIds
        }
        val refreshed = getCachedExamBasics().map { it.copy(isOfflineReady = false) }
        saveExamBasics(refreshed)
        return removedIds.size
    }

    private fun importLegacyPreferencesOnce() {
        dbCall {
            if (dao.getMetadata(KEY_LEGACY_IMPORT_COMPLETE) == "1") return@dbCall
            val all = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).all
            database.runInTransaction {
                importLegacyStaticJson(all)
                importLegacyExamData(all)
                saveMetadataInDao(KEY_LEGACY_IMPORT_COMPLETE, "1")
            }
        }
    }

    private fun importLegacyStaticJson(all: Map<String, *>) {
        importJsonList<Exam>(all[LEGACY_EXAM_BASICS] as? String, NAMESPACE_EXAMS) { it.id }
        importJsonList<SchoolClass>(all[LEGACY_CLASS_BASICS] as? String, NAMESPACE_CLASSES) { it.id }
        importJsonList<AppNotification>(all[LEGACY_NOTIFICATIONS] as? String, NAMESPACE_NOTIFICATIONS) { it.id }
        (all[LEGACY_DISMISSED_NOTIFICATION_IDS] as? String)?.let {
            saveMetadataInDao(KEY_DISMISSED_NOTIFICATION_IDS, it)
        }
        val markedIds: List<String> = parseJson(all[LEGACY_OFFLINE_IDS] as? String, emptyList())
        markedIds.forEach { examId ->
            val current = dao.getOfflineExam(examId) ?: OfflineExamEntity(examId)
            dao.upsertOfflineExam(current.copy(offlineMarked = true, updatedAt = now()))
        }
    }

    private fun importLegacyExamData(all: Map<String, *>) {
        val examIds = linkedSetOf<String>()
        all.keys.forEach { key ->
            LEGACY_EXAM_PREFIXES.firstNotNullOfOrNull { prefix ->
                key.removePrefix(prefix).takeIf { key.startsWith(prefix) && it.isNotBlank() }
            }?.let(examIds::add)
        }
        examIds.forEach { examId ->
            val current = dao.getOfflineExam(examId) ?: OfflineExamEntity(examId)
            dao.upsertOfflineExam(
                current.copy(
                    templateJson = all["template_$examId"] as? String ?: current.templateJson,
                    questionMetadataJson = all["question_metadata_$examId"] as? String ?: current.questionMetadataJson,
                    detailJson = all["exam_detail_$examId"] as? String ?: current.detailJson,
                    classCode = all["exam_class_code_$examId"] as? String ?: current.classCode,
                    updatedAt = now()
                )
            )
        }
    }

    private fun updateOfflineExam(examId: String, update: (OfflineExamEntity) -> OfflineExamEntity) {
        if (examId.isBlank()) return
        synchronized(mutationLock) {
            dbCall {
                database.runInTransaction {
                    val current = dao.getOfflineExam(examId) ?: OfflineExamEntity(examId)
                    dao.upsertOfflineExam(update(current).copy(updatedAt = now()))
                }
            }
        }
    }

    private fun isOfflineReadyInDao(examId: String): Boolean =
        dao.getOfflineExam(examId)?.let {
            it.templateJson != null && it.questionMetadataJson != null
        } == true

    private fun saveMetadata(key: String, value: String) =
        dbCall { saveMetadataInDao(key, value) }

    private fun saveMetadataInDao(key: String, value: String) {
        dao.upsertMetadata(CacheMetadataEntity(key, value, now()))
    }

    private inline fun <reified T> readJsonNamespace(namespace: String): List<T> =
        dao.getJsonNamespace(namespace).mapNotNull { parseJson<T>(it.json) }

    private inline fun <reified T> importJsonList(
        raw: String?,
        namespace: String,
        keyOf: (T) -> String
    ) {
        val items: List<T> = parseJson(raw, emptyList())
        if (items.isNotEmpty()) {
            dao.upsertJson(items.mapIndexed { index, item ->
                jsonEntity(namespace, keyOf(item), gson.toJson(item), index.toLong())
            })
        }
    }

    private fun jsonEntity(namespace: String, key: String, json: String, order: Long) =
        JsonCacheEntity(namespace, key, json, order, now())

    private inline fun <reified T> parseJson(raw: String?): T? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            gson.fromJson<T>(raw, object : TypeToken<T>() {}.type)
        }.getOrNull()
    }

    private inline fun <reified T> parseJson(raw: String?, fallback: T): T =
        parseJson<T>(raw) ?: fallback

    private fun <T> dbCall(block: () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    private fun String?.normalizeIdentity(): String = orEmpty().trim().uppercase()

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val PREFS_NAME = "offline_cache"
        const val NAMESPACE_EXAMS = "exam_basics"
        const val NAMESPACE_CLASSES = "class_basics"
        const val NAMESPACE_NOTIFICATIONS = "notifications"
        const val NAMESPACE_AUTH_CACHE = "auth_cache"
        const val KEY_DISMISSED_NOTIFICATION_IDS = "dismissed_notification_ids"
        const val KEY_LEGACY_IMPORT_COMPLETE = "legacy_offline_cache_import_complete"

        const val LEGACY_OFFLINE_IDS = "offline_exam_ids"
        const val LEGACY_EXAM_BASICS = "exam_basics"
        const val LEGACY_CLASS_BASICS = "class_basics"
        const val LEGACY_NOTIFICATIONS = "notifications"
        const val LEGACY_DISMISSED_NOTIFICATION_IDS = "dismissed_notification_ids"

        val LEGACY_EXAM_PREFIXES = listOf(
            "question_metadata_",
            "exam_class_code_",
            "exam_detail_",
            "template_"
        )
    }
}
