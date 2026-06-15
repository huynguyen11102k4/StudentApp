package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "queued_violations", indices = [Index("sessionId"), Index("queuedAt")])
data class QueuedViolationEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val encryptedJson: String,
    val queuedAt: Long
)
