package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.OmrApplication
import com.examhub.student.R
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
        safeEnvelopeFlow(gson) { apiService.logViolation(request.withCanonicalMessages()) }

    override fun queueViolation(request: LockViolationRequest) {
        violationQueueManager.enqueue(request.withCanonicalMessages())
    }

    override fun flushQueuedViolations(): Flow<ApiResult<Int>> = flow {
        emit(ApiResult.Loading)
        var flushed = 0
        val queued = violationQueueManager.peekAll()
        for (item in queued) {
            val response = apiService.logViolation(item.request.withCanonicalMessages())
            if (response.isSuccessful && response.body()?.data != null) {
                violationQueueManager.remove(item.id)
                flushed += 1
            } else if (response.code() == 400 || response.code() == 404) {
                violationQueueManager.remove(item.id)
            } else {
                break
            }
        }
        emit(ApiResult.Success(flushed))
    }

    override fun queuedViolationCount(): Int = violationQueueManager.count()

    override fun heartbeat(sessionId: String, request: LockHeartbeatRequest): Flow<ApiResult<LockHeartbeatResponse>> =
        safeEnvelopeFlow(gson) { apiService.heartbeat(sessionId, request) }

    private fun LockViolationRequest.withCanonicalMessages(): LockViolationRequest {
        val messages = when (violationType) {
            "switch_app" -> string(R.string.lock_violation_background_label) to
                string(R.string.lock_violation_background_teacher_message)
            "screen_off" -> string(R.string.lock_violation_screen_off_label) to
                string(R.string.lock_violation_screen_off_teacher_message)
            "network_lost" -> string(R.string.lock_violation_network_lost_label) to
                string(R.string.lock_violation_network_lost_teacher_message)
            else -> return this
        }
        return copy(
            evidenceData = evidenceData + mapOf(
                "violation_label" to messages.first,
                "teacher_message" to messages.second
            )
        )
    }

    private fun string(resId: Int): String = OmrApplication.appContext.getString(resId)
}
