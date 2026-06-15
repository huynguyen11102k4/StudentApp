package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "active_exam_sessions",
    indices = [Index(value = ["sessionId"], unique = true)]
)
data class ActiveExamSessionEntity(
    @PrimaryKey val examId: String,
    val sessionId: String,
    val encryptedJson: String,
    val updatedAt: Long
)
