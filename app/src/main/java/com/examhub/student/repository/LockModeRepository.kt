package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.LockHeartbeatRequest
import com.examhub.student.model.request.LockValidateSessionRequest
import com.examhub.student.model.request.LockViolationRequest
import com.examhub.student.model.response.LockHeartbeatResponse
import com.examhub.student.model.response.LockValidateSessionResponse
import com.examhub.student.model.response.LockViolationResponse
import kotlinx.coroutines.flow.Flow

interface LockModeRepository {
    fun validateSession(request: LockValidateSessionRequest): Flow<ApiResult<LockValidateSessionResponse>>
    fun logViolation(request: LockViolationRequest): Flow<ApiResult<LockViolationResponse>>
    fun queueViolation(request: LockViolationRequest)
    fun flushQueuedViolations(): Flow<ApiResult<Int>>
    fun queuedViolationCount(): Int
    fun heartbeat(sessionId: String, request: LockHeartbeatRequest): Flow<ApiResult<LockHeartbeatResponse>>
}
