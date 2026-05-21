package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.StudentAppealRequest
import com.examhub.student.model.response.AppealSummaryResponse
import com.examhub.student.model.response.PagedEnvelope
import com.examhub.student.model.response.StudentAppealCreateResponse
import kotlinx.coroutines.flow.Flow

interface AppealsRepository {
    fun getAppeals(
        status: String? = null,
        examId: String? = null,
        page: String = "1",
        limit: String = "20"
    ): Flow<ApiResult<PagedEnvelope<AppealSummaryResponse>>>

    fun getAppealDetail(appealId: String): Flow<ApiResult<AppealSummaryResponse>>

    fun createAppeal(request: StudentAppealRequest): Flow<ApiResult<StudentAppealCreateResponse>>
}
