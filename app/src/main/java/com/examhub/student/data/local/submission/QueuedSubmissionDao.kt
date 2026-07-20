package com.examhub.student.data.local.submission

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueuedSubmissionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: QueuedSubmissionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(item: QueuedSubmissionEntity)

    @Query("SELECT * FROM queued_submissions WHERE clientSubmissionId = :id LIMIT 1")
    suspend fun get(id: String): QueuedSubmissionEntity?

    @Query("SELECT * FROM queued_submissions WHERE status IN ('PENDING_SYNC', 'UPLOADING_IMAGES', 'SYNCING', 'FAILED_TERMINAL') ORDER BY createdAtMillis ASC")
    suspend fun getPending(): List<QueuedSubmissionEntity>

    @Query("""
        SELECT * FROM queued_submissions
        WHERE examId IN (:examIds)
            AND status IN (:statuses)
        ORDER BY createdAtMillis DESC
    """)
    suspend fun getByExamIdsAndStatuses(
        examIds: List<String>,
        statuses: List<String>
    ): List<QueuedSubmissionEntity>

    @Query("SELECT * FROM queued_submissions ORDER BY createdAtMillis ASC")
    fun getAll(): List<QueuedSubmissionEntity>

    @Query("SELECT * FROM queued_submissions WHERE clientSubmissionId = :id LIMIT 1")
    fun observe(id: String): Flow<QueuedSubmissionEntity?>

    @Query("""
        UPDATE queued_submissions
        SET status = :status, updatedAtMillis = :updatedAt, lastErrorCode = :errorCode, lastErrorMessage = :errorMessage
        WHERE clientSubmissionId = :id
    """)
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Long,
        errorCode: String? = null,
        errorMessage: String? = null
    )

    @Query("""
        UPDATE queued_submissions
        SET encryptedPayload = :encryptedPayload,
            updatedAtMillis = :updatedAt
        WHERE clientSubmissionId = :id
    """)
    suspend fun updateEncryptedPayload(
        id: String,
        encryptedPayload: String,
        updatedAt: Long
    )

    @Query("""
        UPDATE queued_submissions
        SET status = 'SYNCED',
            updatedAtMillis = :updatedAt,
            lastErrorCode = NULL,
            lastErrorMessage = NULL,
            serverSubmissionId = :submissionId,
            resultId = :resultId,
            serverStatus = :serverStatus
        WHERE clientSubmissionId = :id
    """)
    suspend fun markSynced(
        id: String,
        updatedAt: Long,
        submissionId: String?,
        resultId: String?,
        serverStatus: String?
    )

    @Query("DELETE FROM queued_submissions")
    fun deleteAll()
}
