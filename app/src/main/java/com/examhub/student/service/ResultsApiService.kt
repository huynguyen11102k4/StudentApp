package com.examhub.student.service

import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.model.response.result.StudentResultSummaryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ResultsApiService {
    @GET("student/results")
    suspend fun getResults(
        @Query("page") page: String = "1",
        @Query("limit") limit: String = "20"
    ): Response<PagedEnvelope<StudentResultSummaryResponse>>

    @GET("student/results/{sheetId}")
    suspend fun getResultDetail(@Path("sheetId") sheetId: String): Response<ApiEnvelope<StudentResultDetailResponse>>
}
