package com.examhub.student.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.examhub.student.data.local.entity.ActiveExamSessionEntity
import com.examhub.student.data.local.entity.CacheMetadataEntity
import com.examhub.student.data.local.entity.ETagCacheEntity
import com.examhub.student.data.local.entity.JsonCacheEntity
import com.examhub.student.data.local.entity.OfflineExamEntity
import com.examhub.student.data.local.entity.QueuedViolationEntity
import com.examhub.student.data.local.entity.StudentIdentityEntity
import com.examhub.student.data.local.submission.QueuedSubmissionDao
import com.examhub.student.data.local.submission.QueuedSubmissionEntity

@Database(
    entities = [
        OfflineExamEntity::class,
        JsonCacheEntity::class,
        CacheMetadataEntity::class,
        ActiveExamSessionEntity::class,
        QueuedViolationEntity::class,
        ETagCacheEntity::class,
        StudentIdentityEntity::class,
        QueuedSubmissionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class StudentAppDatabase : RoomDatabase() {
    abstract fun studentCacheDao(): StudentCacheDao
    abstract fun queuedSubmissionDao(): QueuedSubmissionDao
}
