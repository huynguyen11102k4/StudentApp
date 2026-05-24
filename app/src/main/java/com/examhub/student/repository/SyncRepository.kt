package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.sync.SyncPushRequest
import com.examhub.student.model.response.sync.SyncPullResponse
import com.examhub.student.model.response.sync.SyncPushEnvelope
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    fun pushSync(request: SyncPushRequest): Flow<ApiResult<SyncPushEnvelope>>
    fun pullSync(since: String): Flow<ApiResult<SyncPullResponse>>
}
