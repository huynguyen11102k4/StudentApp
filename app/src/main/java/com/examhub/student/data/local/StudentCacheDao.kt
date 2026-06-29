package com.examhub.student.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.examhub.student.data.local.entity.ActiveExamSessionEntity
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.ETagCacheEntity
import com.examhub.student.data.local.entity.JsonCacheEntity
import com.examhub.student.data.local.entity.OfflineExamEntity
import com.examhub.student.data.local.entity.QueuedViolationEntity
import com.examhub.student.data.local.entity.StudentIdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertOfflineExam(entity: OfflineExamEntity)

    @Query("SELECT * FROM offline_exams WHERE examId = :examId LIMIT 1")
    fun getOfflineExam(examId: String): OfflineExamEntity?

    @Query("SELECT * FROM offline_exams ORDER BY updatedAt")
    fun getOfflineExams(): List<OfflineExamEntity>

    @Query("DELETE FROM offline_exams WHERE examId = :examId")
    fun deleteOfflineExam(examId: String)

    @Query("DELETE FROM offline_exams")
    fun deleteAllOfflineExams()

    @Query("DELETE FROM offline_exams WHERE examId IN (:examIds)")
    fun deleteOfflineExams(examIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertJson(entity: JsonCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertJson(entities: List<JsonCacheEntity>)

    @Query("SELECT * FROM json_cache WHERE namespace = :namespace ORDER BY sortOrder, updatedAt")
    fun getJsonNamespace(namespace: String): List<JsonCacheEntity>

    @Query("SELECT * FROM json_cache WHERE namespace = :namespace AND recordKey = :recordKey LIMIT 1")
    fun getJson(namespace: String, recordKey: String): JsonCacheEntity?

    @Query("DELETE FROM json_cache WHERE namespace = :namespace")
    fun deleteJsonNamespace(namespace: String)

    @Query("DELETE FROM json_cache WHERE namespace = :namespace AND recordKey = :recordKey")
    fun deleteJson(namespace: String, recordKey: String)

    @Query("DELETE FROM json_cache WHERE namespace = :namespace AND recordKey IN (:recordKeys)")
    fun deleteJson(namespace: String, recordKeys: List<String>)

    @Query("DELETE FROM json_cache WHERE namespace = :namespace AND recordKey NOT IN (:recordKeys)")
    fun deleteJsonNotIn(namespace: String, recordKeys: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMetadata(entity: CacheMetadataEntity)

    @Query("SELECT value FROM cache_metadata WHERE `key` = :key LIMIT 1")
    fun getMetadata(key: String): String?

    @Query("DELETE FROM cache_metadata WHERE `key` = :key")
    fun deleteMetadata(key: String)

    @Query("DELETE FROM cache_metadata WHERE SUBSTR(`key`, 1, LENGTH(:keyPrefix)) = :keyPrefix")
    fun deleteMetadataWithPrefix(keyPrefix: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertActiveSession(entity: ActiveExamSessionEntity)

    @Query("SELECT * FROM active_exam_sessions WHERE examId = :examId LIMIT 1")
    fun getActiveSession(examId: String): ActiveExamSessionEntity?

    @Query("SELECT * FROM active_exam_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun getActiveSessionBySessionId(sessionId: String): ActiveExamSessionEntity?

    @Query("DELETE FROM active_exam_sessions WHERE examId = :examId")
    fun deleteActiveSession(examId: String)

    @Query("DELETE FROM active_exam_sessions WHERE sessionId = :sessionId")
    fun deleteActiveSessionBySessionId(sessionId: String)

    @Query("SELECT examId FROM active_exam_sessions")
    fun getActiveSessionExamIds(): List<String>

    @Query("DELETE FROM active_exam_sessions")
    fun deleteAllActiveSessions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertViolation(entity: QueuedViolationEntity)

    @Query("SELECT * FROM queued_violations ORDER BY queuedAt")
    fun getViolations(): List<QueuedViolationEntity>

    @Query("DELETE FROM queued_violations WHERE id = :id")
    fun deleteViolation(id: String)

    @Query("DELETE FROM queued_violations")
    fun deleteAllViolations()

    @Query("""
        DELETE FROM queued_violations
        WHERE id NOT IN (
            SELECT id FROM queued_violations ORDER BY queuedAt DESC LIMIT :maxSize
        )
    """)
    fun trimViolations(maxSize: Int)

    @Query("SELECT COUNT(*) FROM queued_violations")
    fun violationCount(): Int

    @Query("SELECT COUNT(*) FROM queued_violations")
    fun observeViolationCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEtag(entity: ETagCacheEntity)

    @Query("SELECT * FROM etag_cache WHERE cacheKey = :key LIMIT 1")
    fun getEtag(key: String): ETagCacheEntity?

    @Query("DELETE FROM etag_cache")
    fun deleteAllEtags()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStudentIdentity(entity: StudentIdentityEntity)

    @Query("""
        SELECT * FROM student_identities
        WHERE stableId = :normalized
           OR UPPER(TRIM(COALESCE(internalCode, ''))) = :normalized
           OR UPPER(TRIM(COALESCE(externalCode, ''))) = :normalized
        LIMIT 1
    """)
    fun findStudentIdentity(normalized: String): StudentIdentityEntity?

    @Query("DELETE FROM student_identities")
    fun deleteAllStudentIdentities()
}
