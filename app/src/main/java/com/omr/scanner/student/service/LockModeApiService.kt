package com.omr.scanner.student.service

import com.omr.scanner.student.model.request.LockHeartbeatRequest
import com.omr.scanner.student.model.request.LockValidateSessionRequest
import com.omr.scanner.student.model.request.LockViolationRequest
import com.omr.scanner.student.model.response.ApiEnvelope
import com.omr.scanner.student.model.response.LockHeartbeatResponse
import com.omr.scanner.student.model.response.LockValidateSessionResponse
import com.omr.scanner.student.model.response.LockViolationResponse
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
}
