package com.examhub.student.service

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.examhub.student.data.local.StudentAppDatabase
import com.examhub.student.data.local.StudentDatabaseMigrations
import com.examhub.student.data.local.entity.ActiveExamSessionEntity
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.QueuedViolationEntity
import com.examhub.student.data.local.model.SubmissionSyncStatus
import com.examhub.student.data.local.submission.QueuedSubmissionEntity
import com.examhub.student.data.model.AppNotification
import com.examhub.student.data.model.Exam
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.response.profile.StudentProfileResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.security.EncryptedSubmissionFileStore
import com.examhub.student.security.KeystoreCrypto
import com.google.gson.Gson
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertTrue(cache.isOfflineReady(EXAM_ID))
        cache.markOfflineReady(EXAM_ID)

        assertTrue(cache.isOfflineReady(EXAM_ID))
        assertEquals(listOf(EXAM_ID), cache.getOfflineExamIds())
    }

    @Test
    fun cachedExamBasicsBecomeOfflineReadyWithTemplateOnly() {
        cache.saveExamBasics(listOf(exam()))

        assertFalse(cache.getCachedExamBasic(EXAM_ID)?.isOfflineReady ?: true)
        assertTrue(cache.getOfflineReadyExamBasics().isEmpty())

        cache.saveTemplate(EXAM_ID, """{"gridConfig":{"answer_zones":[]}}""")

        assertTrue(cache.getCachedExamBasic(EXAM_ID)?.isOfflineReady ?: false)
        assertEquals(listOf(EXAM_ID), cache.getOfflineReadyExamBasics().map { it.id })
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
    fun syncedSubmissionKeepsServerNavigationMetadata() = runBlocking {
        val dao = database.queuedSubmissionDao()
        dao.insert(pendingSubmission("client-result"))

        dao.markSynced(
            id = "client-result",
            updatedAt = 2,
            submissionId = "submission-1",
            resultId = "sheet-1",
            serverStatus = "PENDING_GRADING"
        )

        val synced = dao.get("client-result")
        assertEquals(SubmissionSyncStatus.SYNCED.name, synced?.status)
        assertEquals("submission-1", synced?.serverSubmissionId)
        assertEquals("sheet-1", synced?.resultId)
        assertEquals("PENDING_GRADING", synced?.serverStatus)
        assertTrue(dao.getPending().isEmpty())
    }

    @Test
    fun migrationOneToTwoKeepsQueuedSubmissionsAndAddsServerMetadataColumns() {
        database.close()
        val dbName = "migration-1-2-test.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS queued_submissions (
                                clientSubmissionId TEXT NOT NULL,
                                sessionId TEXT NOT NULL,
                                examId TEXT NOT NULL,
                                deviceId TEXT NOT NULL,
                                capturedAt TEXT NOT NULL,
                                encryptedPayload TEXT NOT NULL,
                                rawImagePath TEXT,
                                dewarpedImagePath TEXT,
                                processedImagePath TEXT,
                                status TEXT NOT NULL,
                                createdAtMillis INTEGER NOT NULL,
                                updatedAtMillis INTEGER NOT NULL,
                                lastErrorCode TEXT,
                                lastErrorMessage TEXT,
                                PRIMARY KEY(clientSubmissionId)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO queued_submissions (
                clientSubmissionId, sessionId, examId, deviceId, capturedAt, encryptedPayload,
                rawImagePath, dewarpedImagePath, processedImagePath, status, createdAtMillis,
                updatedAtMillis, lastErrorCode, lastErrorMessage
            ) VALUES (
                'client-before-upgrade', 'session-1', '$EXAM_ID', 'device-1',
                '2026-06-13T00:00:00.000Z', 'encrypted', NULL, NULL, NULL,
                'PENDING_SYNC', 1, 1, NULL, NULL
            )
            """.trimIndent()
        )

        StudentDatabaseMigrations.MIGRATION_1_2.migrate(db)

        db.query("SELECT * FROM queued_submissions WHERE clientSubmissionId = 'client-before-upgrade'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING_SYNC", cursor.getString(cursor.getColumnIndexOrThrow("status")))
                assertTrue(cursor.getColumnIndex("serverSubmissionId") >= 0)
                assertTrue(cursor.getColumnIndex("resultId") >= 0)
                assertTrue(cursor.getColumnIndex("serverStatus") >= 0)
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("resultId")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("serverStatus")))
            }
        helper.close()
        context.deleteDatabase(dbName)

        database = Room.inMemoryDatabaseBuilder(context, StudentAppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = OfflineCacheManager(context, database, gson)
    }

    @Test
    fun clearOfflineDownloadsKeepsActiveExamAndPendingSubmission() = runBlocking {
        cache.saveTemplate("active-exam", """{"template":true}""")
        cache.markOfflineReady("active-exam")
        cache.saveTemplate("downloaded-exam", """{"template":true}""")
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
        cache.markOfflineReady(EXAM_ID)
        database.studentCacheDao().upsertMetadata(
            CacheMetadataEntity("offline_permit:session-1", "encrypted-permit", 1)
        )
        database.studentCacheDao().upsertViolation(
            QueuedViolationEntity(
                id = "violation-1",
                sessionId = "session-1",
                encryptedJson = "encrypted-violation",
                queuedAt = 1
            )
        )
        database.queuedSubmissionDao().insert(pendingSubmission("pending-2"))

        cache.clearUserScopedLists()

        assertTrue(cache.getCachedExamBasics().isEmpty())
        assertTrue(cache.getCachedClassBasics().isEmpty())
        assertTrue(cache.getCachedNotifications().isEmpty())
        assertFalse(cache.isOfflineReady(EXAM_ID))
        assertNull(database.studentCacheDao().getMetadata("offline_permit:session-1"))
        assertEquals(0, database.studentCacheDao().violationCount())
        assertNotNull(database.queuedSubmissionDao().get("pending-2"))
    }

    @Test
    fun backendSwitchCleanupRemovesQueuedSubmissionsAndServerBoundState() = runBlocking {
        cache.saveExamBasics(listOf(exam()))
        cache.saveNotifications(listOf(notification()))
        cache.saveTemplate(EXAM_ID, """{"template":true}""")
        database.studentCacheDao().upsertMetadata(
            CacheMetadataEntity("offline_permit:session-1", "encrypted-permit", 1)
        )
        database.queuedSubmissionDao().insert(pendingSubmission("pending-backend"))

        val crypto = KeystoreCrypto()
        val tokenManager = TokenManager(context, crypto, database)
        val backendUrlManager = BackendUrlManager(
            context = context,
            tokenManager = tokenManager,
            offlineCacheManager = cache,
            queuedSubmissionDao = database.queuedSubmissionDao(),
            submissionFileStore = EncryptedSubmissionFileStore(context, crypto)
        )

        val result = backendUrlManager.saveOverride("http://127.0.0.1:3999")

        assertEquals(BackendUrlUpdateResult.Changed, result)
        assertTrue(cache.getCachedExamBasics().isEmpty())
        assertTrue(cache.getCachedNotifications().isEmpty())
        assertFalse(cache.isOfflineReady(EXAM_ID))
        assertNull(database.studentCacheDao().getMetadata("offline_permit:session-1"))
        assertNull(database.queuedSubmissionDao().get("pending-backend"))
    }

    @Test
    fun replacingClassBasicsDropsDeletedClassesAndKeepsOfflineMetadata() {
        cache.saveClassBasics(
            listOf(
                schoolClass(id = "class-1", name = "Old class", hasOfflineData = true),
                schoolClass(id = "class-2", name = "Deleted class", hasOfflineData = true)
            )
        )

        cache.saveClassBasics(
            listOf(
                schoolClass(id = "class-1", name = "Fresh class"),
                schoolClass(id = "class-3", name = "New class")
            ),
            replaceExisting = true
        )

        val classes = cache.getCachedClassBasics()

        assertEquals(listOf("class-1", "class-3"), classes.map { it.id })
        assertEquals("Fresh class", classes.first { it.id == "class-1" }.name)
        assertTrue(classes.first { it.id == "class-1" }.hasOfflineData)
        assertFalse(classes.first { it.id == "class-3" }.hasOfflineData)
    }

    @Test
    fun replacingClassBasicsWithEmptySnapshotClearsCachedClasses() {
        cache.saveClassBasics(listOf(schoolClass(id = "class-1")))

        cache.saveClassBasics(emptyList(), replaceExisting = true)

        assertTrue(cache.getCachedClassBasics().isEmpty())
    }

    @Test
    fun dismissNotificationsOnlyRemovesSelectedNotificationsFromCache() {
        cache.saveNotifications(
            listOf(
                notification("notification-1", isRead = false),
                notification("notification-2", isRead = true),
                notification("notification-3", isRead = false)
            )
        )

        cache.dismissNotifications(listOf("notification-1", "notification-2"))

        assertEquals(listOf("notification-3"), cache.getCachedNotifications().map { it.id })
        assertEquals(
            setOf("notification-1", "notification-2"),
            cache.getDismissedNotificationIds().toSet()
        )
    }

    @Test
    fun notificationSnapshotDropsServerDeletedNotificationsWhenAllPagesLoaded() {
        cache.saveNotifications(
            listOf(
                notification("notification-1", isRead = false),
                notification("notification-2", isRead = true),
                notification("notification-stale", isRead = false)
            )
        )

        cache.saveNotificationSnapshotPage(
            notifications = listOf(notification("notification-1", isRead = true)),
            page = 1,
            total = 2
        )
        assertEquals(
            setOf("notification-1", "notification-2", "notification-stale"),
            cache.getCachedNotifications().map { it.id }.toSet()
        )

        cache.saveNotificationSnapshotPage(
            notifications = listOf(notification("notification-2", isRead = true)),
            page = 2,
            total = 2
        )

        assertEquals(
            listOf("notification-1", "notification-2"),
            cache.getCachedNotifications().map { it.id }
        )
        assertTrue(cache.getCachedNotifications().all { it.isRead })
    }

    @Test
    fun emptyNotificationSnapshotClearsCachedNotificationsButKeepsDismissedIds() {
        cache.saveNotifications(listOf(notification("notification-1")))
        cache.dismissNotifications(listOf("notification-dismissed"))

        cache.saveNotificationSnapshotPage(emptyList(), page = 1, total = 0)

        assertTrue(cache.getCachedNotifications().isEmpty())
        assertEquals(listOf("notification-dismissed"), cache.getDismissedNotificationIds())
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

    private fun schoolClass(
        id: String = "class-1",
        name: String = "Class",
        hasOfflineData: Boolean = false
    ) = SchoolClass(
        id = id,
        name = name,
        subject = "Math",
        joinCode = "JOIN",
        studentCount = 1,
        hasOfflineData = hasOfflineData
    )

    private fun notification(id: String = "notification-1", isRead: Boolean = false) = AppNotification(
        id = id,
        type = "EXAM",
        title = "Title",
        content = "Content",
        link = null,
        appealId = null,
        isRead = isRead,
        createdAt = "2026-06-13T00:00:00Z"
    )

    private companion object {
        const val PREFS_NAME = "offline_cache"
        const val EXAM_ID = "exam-1"
    }
}
