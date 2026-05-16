package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.LockHeartbeatRequest
import com.omr.scanner.student.model.request.LockValidateSessionRequest
import com.omr.scanner.student.model.request.LockViolationRequest
import com.omr.scanner.student.model.response.LockHeartbeatResponse
import com.omr.scanner.student.model.response.LockValidateSessionResponse
import com.omr.scanner.student.model.response.LockViolationResponse
import com.omr.scanner.student.repository.LockModeRepository
import com.omr.scanner.student.service.LockModeApiService
import com.omr.scanner.student.service.ViolationQueueManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LockModeRepositoryImpl(
    private val apiService: LockModeApiService,
    private val gson: Gson,
    private val violationQueueManager: ViolationQueueManager
) : LockModeRepository {
    override fun validateSession(request: LockValidateSessionRequest): Flow<ApiResult<LockValidateSessionResponse>> =
        safeEnvelopeFlow(gson) { apiService.validateSession(request) }

    override fun logViolation(request: LockViolationRequest): Flow<ApiResult<LockViolationResponse>> =
        safeEnvelopeFlow(gson) { apiService.logViolation(request) }

    override fun queueViolation(request: LockViolationRequest) {
        violationQueueManager.enqueue(request)
    }

    override fun flushQueuedViolations(): Flow<ApiResult<Int>> = flow {
        emit(ApiResult.Loading)
        var flushed = 0
        val queued = violationQueueManager.peekAll()
        for (item in queued) {
            val response = apiService.logViolation(item.request)
            if (response.isSuccessful && response.body()?.data != null) {
                violationQueueManager.remove(item.id)
                flushed += 1
            } else {
                break
            }
        }
        emit(ApiResult.Success(flushed))
    }

    override fun queuedViolationCount(): Int = violationQueueManager.count()

    override fun heartbeat(sessionId: String, request: LockHeartbeatRequest): Flow<ApiResult<LockHeartbeatResponse>> =
        safeEnvelopeFlow(gson) { apiService.heartbeat(sessionId, request) }
}
