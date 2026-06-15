package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "student_identities",
    indices = [Index("internalCode"), Index("externalCode")]
)
data class StudentIdentityEntity(
    @PrimaryKey val stableId: String,
    val internalCode: String?,
    val externalCode: String?,
    val fullName: String?,
    val updatedAt: Long
)
