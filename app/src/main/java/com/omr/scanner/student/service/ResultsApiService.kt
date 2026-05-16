package com.omr.scanner.student.service

import com.omr.scanner.student.model.response.ApiEnvelope
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.StudentResultDetailResponse
import com.omr.scanner.student.model.response.StudentResultSummaryResponse
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
