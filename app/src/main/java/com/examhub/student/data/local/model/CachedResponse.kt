package com.examhub.student.data.local.model

data class CachedResponse(
    val etag: String,
    val body: String,
    val contentType: String?,
    val code: Int,
    val message: String
)
