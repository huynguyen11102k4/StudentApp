package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.StudentResultDetailResponse
import com.omr.scanner.student.model.response.StudentResultSummaryResponse
import com.omr.scanner.student.repository.ResultsRepository
import com.omr.scanner.student.service.ResultsApiService
import kotlinx.coroutines.flow.Flow

class ResultsRepositoryImpl(
    private val apiService: ResultsApiService,
    private val gson: Gson
) : ResultsRepository {
    override fun getResults(page: String, limit: String): Flow<ApiResult<PagedEnvelope<StudentResultSummaryResponse>>> =
        safeApiFlow(gson) { apiService.getResults(page, limit) }

    override fun getResultDetail(sheetId: String): Flow<ApiResult<StudentResultDetailResponse>> =
        safeEnvelopeFlow(gson) { apiService.getResultDetail(sheetId) }
}
