package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.StudentResultDetailResponse
import com.omr.scanner.student.model.response.StudentResultSummaryResponse
import kotlinx.coroutines.flow.Flow

interface ResultsRepository {
    fun getResults(page: String = "1", limit: String = "20"): Flow<ApiResult<PagedEnvelope<StudentResultSummaryResponse>>>
    fun getResultDetail(sheetId: String): Flow<ApiResult<StudentResultDetailResponse>>
}
