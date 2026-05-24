package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.result.StudentResultDetailResponse
import com.examhub.student.model.response.result.StudentResultSummaryResponse
import kotlinx.coroutines.flow.Flow

interface ResultsRepository {
    fun getResults(page: String = "1", limit: String = "20"): Flow<ApiResult<PagedEnvelope<StudentResultSummaryResponse>>>
    fun getResultDetail(sheetId: String): Flow<ApiResult<StudentResultDetailResponse>>
}
