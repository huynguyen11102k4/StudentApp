package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.appeal.StudentAppealRequest
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.common.StudentAppealCreateResponse
import com.examhub.student.repository.AppealsRepository
import com.examhub.student.service.AppealsApiService
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
