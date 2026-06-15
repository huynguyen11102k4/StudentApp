package com.examhub.student.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "etag_cache")
data class ETagCacheEntity(
    @PrimaryKey val cacheKey: String,
    val url: String,
    val etag: String,
    val body: String,
    val contentType: String?,
    val code: Int,
    val message: String,
    val updatedAt: Long
)
