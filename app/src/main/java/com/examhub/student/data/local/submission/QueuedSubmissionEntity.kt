package com.examhub.student.data.local.submission

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queued_submissions")
data class QueuedSubmissionEntity(
    @PrimaryKey val clientSubmissionId: String,
    val sessionId: String,
    val examId: String,
    val deviceId: String,
    val capturedAt: String,
    val encryptedPayload: String,
    val rawImagePath: String?,
    val dewarpedImagePath: String?,
    val processedImagePath: String?,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val serverSubmissionId: String? = null,
    val resultId: String? = null,
    val serverStatus: String? = null
)
