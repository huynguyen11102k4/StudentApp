package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.SyncPushRequest
import com.omr.scanner.student.model.response.SyncPullResponse
import com.omr.scanner.student.model.response.SyncPushEnvelope
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun pushSync(request: SyncPushRequest): Flow<ApiResult<SyncPushEnvelope>>
    fun pullSync(since: String): Flow<ApiResult<SyncPullResponse>>
}
