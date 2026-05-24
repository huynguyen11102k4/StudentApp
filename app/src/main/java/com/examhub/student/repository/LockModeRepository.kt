package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.response.lock.LockHeartbeatResponse
import com.examhub.student.model.response.lock.LockValidateSessionResponse
import com.examhub.student.model.response.lock.LockViolationResponse
import kotlinx.coroutines.flow.Flow

interface LockModeRepository {
    fun validateSession(request: LockValidateSessionRequest): Flow<ApiResult<LockValidateSessionResponse>>
    fun logViolation(request: LockViolationRequest): Flow<ApiResult<LockViolationResponse>>
    fun queueViolation(request: LockViolationRequest)
    fun flushQueuedViolations(): Flow<ApiResult<Int>>
    fun queuedViolationCount(): Int
    fun heartbeat(sessionId: String, request: LockHeartbeatRequest): Flow<ApiResult<LockHeartbeatResponse>>
}
