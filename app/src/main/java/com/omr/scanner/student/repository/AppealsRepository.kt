package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.StudentAppealRequest
import com.omr.scanner.student.model.response.AppealSummaryResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.StudentAppealCreateResponse
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
