package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "json_cache",
    primaryKeys = ["namespace", "recordKey"],
    indices = [Index("namespace"), Index(value = ["namespace", "sortOrder"])]
)
data class JsonCacheEntity(
    val namespace: String,
    val recordKey: String,
    val json: String,
    val sortOrder: Long,
    val updatedAt: Long
)
