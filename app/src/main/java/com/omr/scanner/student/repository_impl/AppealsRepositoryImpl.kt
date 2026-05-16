package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.StudentAppealRequest
import com.omr.scanner.student.model.response.AppealSummaryResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.StudentAppealCreateResponse
import com.omr.scanner.student.repository.AppealsRepository
import com.omr.scanner.student.service.AppealsApiService
import kotlinx.coroutines.flow.Flow

class AppealsRepositoryImpl(
    private val apiService: AppealsApiService,
    private val gson: Gson
) : AppealsRepository {
    override fun getAppeals(
        status: String?,
        examId: String?,
        page: String,
        limit: String
    ): Flow<ApiResult<PagedEnvelope<AppealSummaryResponse>>> =
        safeApiFlow(gson) { apiService.getAppeals(status, examId, page, limit) }

    override fun getAppealDetail(appealId: String): Flow<ApiResult<AppealSummaryResponse>> =
        safeEnvelopeFlow(gson) { apiService.getAppealDetail(appealId) }

    override fun createAppeal(request: StudentAppealRequest): Flow<ApiResult<StudentAppealCreateResponse>> =
        safeEnvelopeFlow(gson) { apiService.createAppeal(request) }
}
