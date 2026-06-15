package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_exams")
data class OfflineExamEntity(
    @PrimaryKey val examId: String,
    val templateJson: String? = null,
    val questionMetadataJson: String? = null,
    val detailJson: String? = null,
    val classCode: String? = null,
    val offlineMarked: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
