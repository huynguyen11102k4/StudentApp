package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.appeal.StudentAppealRequest
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.common.StudentAppealCreateResponse
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
