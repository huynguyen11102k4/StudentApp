package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.LockHeartbeatRequest
import com.omr.scanner.student.model.request.LockValidateSessionRequest
import com.omr.scanner.student.model.request.LockViolationRequest
import com.omr.scanner.student.model.response.LockHeartbeatResponse
import com.omr.scanner.student.model.response.LockValidateSessionResponse
import com.omr.scanner.student.model.response.LockViolationResponse
import kotlinx.coroutines.flow.Flow

interface LockModeRepository {
    fun validateSession(request: LockValidateSessionRequest): Flow<ApiResult<LockValidateSessionResponse>>
    fun logViolation(request: LockViolationRequest): Flow<ApiResult<LockViolationResponse>>
    fun queueViolation(request: LockViolationRequest)
    fun flushQueuedViolations(): Flow<ApiResult<Int>>
    fun queuedViolationCount(): Int
    fun heartbeat(sessionId: String, request: LockHeartbeatRequest): Flow<ApiResult<LockHeartbeatResponse>>
}
