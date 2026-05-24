package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.response.lock.LockHeartbeatResponse
import com.examhub.student.model.response.lock.LockValidateSessionResponse
import com.examhub.student.model.response.lock.LockViolationResponse
import com.examhub.student.repository.LockModeRepository
import com.examhub.student.service.LockModeApiService
import com.examhub.student.service.ViolationQueueManager
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
