package com.examhub.student.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object StudentDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE queued_submissions ADD COLUMN serverSubmissionId TEXT")
            db.execSQL("ALTER TABLE queued_submissions ADD COLUMN resultId TEXT")
            db.execSQL("ALTER TABLE queued_submissions ADD COLUMN serverStatus TEXT")
        }
    }
}
