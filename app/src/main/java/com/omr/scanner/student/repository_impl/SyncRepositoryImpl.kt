package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.SyncPushRequest
import com.omr.scanner.student.model.response.SyncPullResponse
import com.omr.scanner.student.model.response.SyncPushEnvelope
import com.omr.scanner.student.repository.SyncRepository
import com.omr.scanner.student.service.SyncApiService
import kotlinx.coroutines.flow.Flow

class SyncRepositoryImpl(
    private val apiService: SyncApiService,
    private val gson: Gson
) : SyncRepository {
    override fun pushSync(request: SyncPushRequest): Flow<ApiResult<SyncPushEnvelope>> =
        safeApiFlow(gson) { apiService.pushSync(request) }

    override fun pullSync(since: String): Flow<ApiResult<SyncPullResponse>> =
        safeEnvelopeFlow(gson) { apiService.pullSync(since) }
}
