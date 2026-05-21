package com.examhub.student.service

import com.examhub.student.model.request.SyncPushRequest
import com.examhub.student.model.response.ApiEnvelope
import com.examhub.student.model.response.SyncPullResponse
import com.examhub.student.model.response.SyncPushEnvelope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApiService {
    @POST("sync/push")
    suspend fun pushSync(@Body request: SyncPushRequest): Response<SyncPushEnvelope>

    @GET("sync/pull")
    suspend fun pullSync(@Query("since") since: String): Response<ApiEnvelope<SyncPullResponse>>
}
