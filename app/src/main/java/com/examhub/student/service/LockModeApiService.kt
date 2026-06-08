package com.examhub.student.service

import com.examhub.student.model.request.lock.LockHeartbeatRequest
import com.examhub.student.model.request.lock.LockValidateSessionRequest
import com.examhub.student.model.request.lock.LockViolationRequest
import com.examhub.student.model.request.lock.LockViolationSyncRequest
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.lock.LockHeartbeatResponse
import com.examhub.student.model.response.lock.LockValidateSessionResponse
import com.examhub.student.model.response.lock.LockViolationResponse
import com.examhub.student.model.response.lock.LockViolationSyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface LockModeApiService {
    @POST("lock/validate-session")
    suspend fun validateSession(@Body request: LockValidateSessionRequest): Response<ApiEnvelope<LockValidateSessionResponse>>

    @POST("lock/log-violation")
    suspend fun logViolation(@Body request: LockViolationRequest): Response<ApiEnvelope<LockViolationResponse>>

    @PUT("lock/sessions/{sessionId}/heartbeat")
    suspend fun heartbeat(
        @Path("sessionId") sessionId: String,
        @Body request: LockHeartbeatRequest
    ): Response<ApiEnvelope<LockHeartbeatResponse>>

    @POST("lock/sessions/{sessionId}/violations/sync")
    suspend fun syncViolations(
        @Path("sessionId") sessionId: String,
        @Body request: LockViolationSyncRequest
    ): Response<ApiEnvelope<LockViolationSyncResponse>>
}
