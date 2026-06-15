package com.examhub.student.data.local.model

import com.examhub.student.model.request.lock.LockViolationRequest

data class QueuedViolation(
    val id: String,
    val request: LockViolationRequest,
    val queuedAt: Long
)
