package com.examhub.student.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.examhub.student.data.model.AppNotification
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass

/**
 * Manages offline-cached exam data.
 * Stored in SharedPreferences so data persists after app restart.
 */
class OfflineCacheManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveTemplate(examId: String, templateJson: String) {
        prefs.edit().putString(keyTemplate(examId), templateJson).apply()
    }

    fun getTemplate(examId: String): String? {
        return prefs.getString(keyTemplate(examId), null)
    }

    fun saveQuestionMetadata(examId: String, questionMetadataJson: String) {
        prefs.edit().putString(keyQuestionMetadata(examId), questionMetadataJson).apply()
    }

    fun getQuestionMetadata(examId: String): String? {
        return prefs.getString(keyQuestionMetadata(examId), null)
    }

    fun saveExamClassCode(examId: String, classCode: String?) {
        if (examId.isBlank() || classCode.isNullOrBlank()) return
        prefs.edit().putString(keyExamClassCode(examId), classCode).apply()
    }

    fun getExamClassCode(examId: String): String? {
        return prefs.getString(keyExamClassCode(examId), null)
    }

    fun isOfflineReady(examId: String): Boolean {
        return getTemplate(examId) != null
    }

    fun markOfflineReady(examId: String) {
        val ids = getOfflineExamIds().toMutableSet()
        ids.add(examId)
        prefs.edit().putString(KEY_OFFLINE_IDS, gson.toJson(ids.toList())).apply()
    }

    fun saveExamBasics(exams: List<Exam>) {
        prefs.edit()
            .putString(KEY_EXAM_BASICS, gson.toJson(exams.map { it.copy(isOfflineReady = isOfflineReady(it.id)) }))
            .apply()
    }

    fun saveExamBasic(exam: Exam) {
        val merged = getCachedExamBasics()
            .associateBy { it.id }
            .toMutableMap()
        merged[exam.id] = exam.copy(isOfflineReady = isOfflineReady(exam.id))
        prefs.edit().putString(KEY_EXAM_BASICS, gson.toJson(merged.values.toList())).apply()
    }

    fun getCachedExamBasics(): List<Exam> {
        val raw = prefs.getString(KEY_EXAM_BASICS, null) ?: return emptyList()
        return try {
            val exams: List<Exam> = gson.fromJson(raw, object : TypeToken<List<Exam>>() {}.type)
            exams.map { it.copy(isOfflineReady = isOfflineReady(it.id)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCachedExamBasic(examId: String): Exam? {
        return getCachedExamBasics().firstOrNull { it.id == examId }
    }

    fun getOfflineReadyExamBasics(): List<Exam> {
        val offlineIds = getOfflineExamIds().toSet()
        return getCachedExamBasics()
            .filter { it.id in offlineIds || isOfflineReady(it.id) }
            .map { it.copy(isOfflineReady = true) }
    }

    fun saveClassBasics(classes: List<SchoolClass>) {
        if (classes.isEmpty()) return
        val merged = getCachedClassBasics()
            .associateBy { it.id }
            .toMutableMap()

        classes.forEach { schoolClass ->
            merged[schoolClass.id] = schoolClass
        }

        prefs.edit().putString(KEY_CLASS_BASICS, gson.toJson(merged.values.toList())).apply()
    }

    fun getCachedClassBasics(): List<SchoolClass> {
        val raw = prefs.getString(KEY_CLASS_BASICS, null) ?: return emptyList()
        return try {
            gson.fromJson(raw, object : TypeToken<List<SchoolClass>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveNotifications(notifications: List<AppNotification>) {
        val dismissedIds = getDismissedNotificationIds().toSet()
        prefs.edit()
            .putString(KEY_NOTIFICATIONS, gson.toJson(notifications.filterNot { it.id in dismissedIds }))
            .apply()
    }

    fun getCachedNotifications(): List<AppNotification> {
        val raw = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
        return try {
            val dismissedIds = getDismissedNotificationIds().toSet()
            val notifications: List<AppNotification> = gson.fromJson(raw, object : TypeToken<List<AppNotification>>() {}.type)
            notifications.filterNot { it.id in dismissedIds }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearNotifications() {
        prefs.edit().remove(KEY_NOTIFICATIONS).apply()
    }

    fun dismissNotifications(notificationIds: List<String>) {
        if (notificationIds.isEmpty()) return
        val merged = getDismissedNotificationIds().toMutableSet()
        merged.addAll(notificationIds.filter { it.isNotBlank() })
        prefs.edit()
            .putString(KEY_DISMISSED_NOTIFICATION_IDS, gson.toJson(merged.toList()))
            .remove(KEY_NOTIFICATIONS)
            .apply()
    }

    fun getDismissedNotificationIds(): List<String> {
        val raw = prefs.getString(KEY_DISMISSED_NOTIFICATION_IDS, null) ?: return emptyList()
        return try {
            gson.fromJson(raw, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getOfflineExamIds(): List<String> {
        val raw = prefs.getString(KEY_OFFLINE_IDS, null) ?: return emptyList()
        return try {
            gson.fromJson(raw, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeOfflineData(examId: String) {
        val ids = getOfflineExamIds().toMutableSet()
        ids.remove(examId)
        prefs.edit()
            .remove(keyTemplate(examId))
            .remove(keyQuestionMetadata(examId))
            .remove(keyExamClassCode(examId))
            .putString(KEY_OFFLINE_IDS, gson.toJson(ids.toList()))
            .apply()
    }

    fun clearUserScopedLists() {
        prefs.edit()
            .remove(KEY_EXAM_BASICS)
            .remove(KEY_CLASS_BASICS)
            .remove(KEY_NOTIFICATIONS)
            .remove(KEY_DISMISSED_NOTIFICATION_IDS)
            .apply()
    }

    fun clearOfflineDownloads(): Int {
        val ids = getOfflineExamIds()
        val editor = prefs.edit()
        ids.forEach { examId ->
            editor
                .remove(keyTemplate(examId))
                .remove(keyQuestionMetadata(examId))
                .remove(keyExamClassCode(examId))
        }
        editor.putString(KEY_OFFLINE_IDS, gson.toJson(emptyList<String>())).apply()

        val refreshedBasics = getCachedExamBasics().map { it.copy(isOfflineReady = false) }
        prefs.edit().putString(KEY_EXAM_BASICS, gson.toJson(refreshedBasics)).apply()
        return ids.size
    }

    private fun keyTemplate(examId: String) = "template_$examId"
    private fun keyQuestionMetadata(examId: String) = "question_metadata_$examId"
    private fun keyExamClassCode(examId: String) = "exam_class_code_$examId"

    private companion object {
        const val PREFS_NAME = "offline_cache"
        const val KEY_OFFLINE_IDS = "offline_exam_ids"
        const val KEY_EXAM_BASICS = "exam_basics"
        const val KEY_CLASS_BASICS = "class_basics"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_DISMISSED_NOTIFICATION_IDS = "dismissed_notification_ids"
    }
}
