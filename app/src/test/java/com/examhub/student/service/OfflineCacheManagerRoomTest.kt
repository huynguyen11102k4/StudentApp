package com.examhub.student.service

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.examhub.student.data.local.LegacySubmissionQueueImporter
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.entity.ActiveExamSessionEntity
import com.examhub.student.data.local.model.SubmissionSyncStatus
import com.examhub.student.data.local.submission.QueuedSubmissionEntity
import com.examhub.student.data.model.AppNotification
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.response.profile.StudentProfileResponse
import com.examhub.student.model.response.profile.UserResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineCacheManagerRoomTest {
    private lateinit var context: Context
    private lateinit var database: StudentAppDatabase
    private lateinit var cache: OfflineCacheManager
    private val gson = Gson()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, StudentAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = OfflineCacheManager(context, database, gson)
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getDatabasePath("student_submission_queue.db").delete()
    }

    @Test
    fun offlineExamIsReadyAfterTemplateIsCached() {
        assertFalse(cache.isOfflineReady(EXAM_ID))
        cache.saveTemplate(EXAM_ID, """{"gridConfig":{"answer_zones":[]}}""")
        assertFalse(cache.isOfflineReady(EXAM_ID))
        cache.saveQuestionMetadata(EXAM_ID, """[{"question_number":1}]""")
        cache.markOfflineReady(EXAM_ID)

        assertTrue(cache.isOfflineReady(EXAM_ID))
        assertEquals(listOf(EXAM_ID), cache.getOfflineExamIds())
    }

    @Test
    fun internalAndExternalCodesResolveToOneStableStudentId() {
        cache.saveStudentIdentity(
            UserResponse(
                id = "user-1",
                email = "student@example.test",
                fullName = "Nguyen Van A",
                role = "STUDENT",
                student = StudentProfileResponse(
                    id = "student-1",
                    internalId = "00000001",
                    studentCode = "SV001"
                )
            )
        )

        assertEquals("STUDENT-1", cache.stableStudentId("00000001"))
        assertEquals("STUDENT-1", cache.stableStudentId("sv001"))
        assertEquals("STUDENT-1", cache.stableStudentId("student-1"))
    }

    @Test
    fun concurrentExamAssetWritesDoNotOverwriteEachOther() {
        val start = CountDownLatch(1)
        val finished = CountDownLatch(3)
        val executor = Executors.newFixedThreadPool(3)
        listOf<() -> Unit>(
            { cache.saveTemplate(EXAM_ID, """{"template":true}""") },
            { cache.saveQuestionMetadata(EXAM_ID, """[{"question_number":1}]""") },
            { cache.saveExamClassCode(EXAM_ID, "CLASS-01") }
        ).forEach { write ->
            executor.execute {
                try {
                    start.await()
                    write()
                } finally {
                    finished.countDown()
                }
            }
        }

        start.countDown()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals("""{"template":true}""", cache.getTemplate(EXAM_ID))
        assertEquals("""[{"question_number":1}]""", cache.getQuestionMetadata(EXAM_ID))
        assertEquals("CLASS-01", cache.getExamClassCode(EXAM_ID))
    }

    @Test
    fun legacyPreferencesAreImportedOnceWithoutDeletingSource() {
        database.close()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("template_$EXAM_ID", """{"mode":"EXTERNAL"}""")
            .putString("question_metadata_$EXAM_ID", """[{"question_number":1}]""")
            .putString("exam_class_code_$EXAM_ID", "CLASS-01")
            .putString("offline_exam_ids", gson.toJson(listOf(EXAM_ID)))
            .commit()

        database = Room.inMemoryDatabaseBuilder(context, StudentAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = OfflineCacheManager(context, database, gson)

        assertTrue(cache.isOfflineReady(EXAM_ID))
        assertEquals("CLASS-01", cache.getExamClassCode(EXAM_ID))
        assertNotNull(prefs.getString("template_$EXAM_ID", null))

        prefs.edit().putString("template_$EXAM_ID", """{"mode":"INTERNAL"}""").commit()
        OfflineCacheManager(context, database, gson)
        assertEquals("""{"mode":"EXTERNAL"}""", cache.getTemplate(EXAM_ID))
    }

    @Test
    fun pendingSubmissionPersistsAndEmitsRoomUpdates() = runBlocking {
        val dao = database.queuedSubmissionDao()
        val item = QueuedSubmissionEntity(
            clientSubmissionId = "client-1",
            sessionId = "session-1",
            examId = EXAM_ID,
            deviceId = "device-1",
            capturedAt = "2026-06-13T00:00:00.000Z",
            encryptedPayload = "encrypted",
            rawImagePath = null,
            dewarpedImagePath = null,
            processedImagePath = null,
            status = "PENDING_SYNC",
            createdAtMillis = 1,
            updatedAtMillis = 1
        )

        dao.insert(item)

        assertEquals(item, dao.get("client-1"))
        assertEquals(item, dao.observe("client-1").first())
        assertEquals(1, dao.getPending().size)
    }

    @Test
    fun interruptedSubmissionStatesAreRecoveredAsPendingWork() = runBlocking {
        val dao = database.queuedSubmissionDao()
        listOf(
            SubmissionSyncStatus.PENDING_SYNC,
            SubmissionSyncStatus.UPLOADING_IMAGES,
            SubmissionSyncStatus.SYNCING,
            SubmissionSyncStatus.SYNCED
        ).forEachIndexed { index, status ->
            dao.insert(pendingSubmission("client-$index").copy(status = status.name))
        }

        assertEquals(
            setOf("client-0", "client-1", "client-2"),
            dao.getPending().map { it.clientSubmissionId }.toSet()
        )
    }

    @Test
    fun failedLegacyQueueReadIsRetriedInsteadOfMarkedComplete() {
        val legacyFile = context.getDatabasePath("student_submission_queue.db")
        legacyFile.parentFile?.mkdirs()
        legacyFile.writeText("not a sqlite database")

        LegacySubmissionQueueImporter(context, database).importOnce()

        assertEquals(
            null,
            database.studentCacheDao()
                .getMetadata("legacy_submission_queue_import_complete")
        )
        legacyFile.delete()
        LegacySubmissionQueueImporter(context, database).importOnce()
        assertEquals(
            "1",
            database.studentCacheDao()
                .getMetadata("legacy_submission_queue_import_complete")
        )
    }

    @Test
    fun clearOfflineDownloadsKeepsActiveExamAndPendingSubmission() = runBlocking {
        cache.saveTemplate("active-exam", """{"template":true}""")
        cache.saveQuestionMetadata("active-exam", "[]")
        cache.markOfflineReady("active-exam")
        cache.saveTemplate("downloaded-exam", """{"template":true}""")
        cache.saveQuestionMetadata("downloaded-exam", "[]")
        cache.markOfflineReady("downloaded-exam")
        database.studentCacheDao().upsertActiveSession(
            ActiveExamSessionEntity(
                examId = "active-exam",
                sessionId = "session-active",
                encryptedJson = "encrypted",
                updatedAt = 1
            )
        )
        database.queuedSubmissionDao().insert(pendingSubmission("pending-1"))

        val removed = cache.clearOfflineDownloads()

        assertEquals(1, removed)
        assertTrue(cache.isOfflineReady("active-exam"))
        assertFalse(cache.isOfflineReady("downloaded-exam"))
        assertNotNull(database.queuedSubmissionDao().get("pending-1"))
    }

    @Test
    fun userScopedCleanupRemovesVisibleCacheButKeepsSubmissionQueue() = runBlocking {
        cache.saveExamBasics(listOf(exam()))
        cache.saveClassBasics(listOf(schoolClass()))
        cache.saveNotifications(listOf(notification()))
        cache.saveTemplate(EXAM_ID, """{"template":true}""")
        cache.saveQuestionMetadata(EXAM_ID, "[]")
        cache.markOfflineReady(EXAM_ID)
        database.queuedSubmissionDao().insert(pendingSubmission("pending-2"))

        cache.clearUserScopedLists()

        assertTrue(cache.getCachedExamBasics().isEmpty())
        assertTrue(cache.getCachedClassBasics().isEmpty())
        assertTrue(cache.getCachedNotifications().isEmpty())
        assertFalse(cache.isOfflineReady(EXAM_ID))
        assertNotNull(database.queuedSubmissionDao().get("pending-2"))
    }

    @Test
    fun dismissNotificationsClearsVisibleNotificationCache() {
        cache.saveNotifications(listOf(notification()))

        cache.dismissNotifications(listOf("notification-1"))

        assertTrue(cache.getCachedNotifications().isEmpty())
        assertEquals(listOf("notification-1"), cache.getDismissedNotificationIds())
    }

    private fun pendingSubmission(id: String) = QueuedSubmissionEntity(
        clientSubmissionId = id,
        sessionId = "session-1",
        examId = EXAM_ID,
        deviceId = "device-1",
        capturedAt = "2026-06-13T00:00:00.000Z",
        encryptedPayload = "encrypted",
        rawImagePath = null,
        dewarpedImagePath = null,
        processedImagePath = null,
        status = "PENDING_SYNC",
        createdAtMillis = 1,
        updatedAtMillis = 1
    )

    private fun exam() = Exam(
        id = EXAM_ID,
        name = "Exam",
        subject = "Math",
        className = "Class",
        duration = 60,
        questionCount = 10,
        status = "ACTIVE",
        gradedCount = 0,
        totalStudents = 1
    )

    private fun schoolClass() = SchoolClass(
        id = "class-1",
        name = "Class",
        subject = "Math",
        joinCode = "JOIN",
        studentCount = 1
    )

    private fun notification() = AppNotification(
        id = "notification-1",
        type = "EXAM",
        title = "Title",
        content = "Content",
        link = null,
        appealId = null,
        isRead = false,
        createdAt = "2026-06-13T00:00:00Z"
    )

    private companion object {
        const val PREFS_NAME = "offline_cache"
        const val EXAM_ID = "exam-1"
    }
}
