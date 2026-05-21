package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.SyncPushRequest
import com.examhub.student.model.response.SyncPullResponse
import com.examhub.student.model.response.SyncPushEnvelope
import com.examhub.student.repository.SyncRepository
import com.examhub.student.service.SyncApiService
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
