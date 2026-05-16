package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.MobileExamDetailResponse
import com.omr.scanner.student.model.response.MobileExamSummaryResponse
import com.omr.scanner.student.model.response.OmrTemplateResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.QuestionMetadataResponse
import com.omr.scanner.student.model.response.StartExamSessionResponse
import kotlinx.coroutines.flow.Flow

interface ExamRepository {
    fun getExams(
        page: String = "1",
        limit: String = "20",
        status: String? = null,
        excludeClosed: Boolean? = null,
        subject: String? = null,
        gradingType: String? = null
    ): Flow<ApiResult<PagedEnvelope<MobileExamSummaryResponse>>>
    fun getExamDetail(examId: String): Flow<ApiResult<MobileExamDetailResponse>>
    fun getExamTemplate(examId: String): Flow<ApiResult<OmrTemplateResponse>>
    fun startSession(examId: String): Flow<ApiResult<StartExamSessionResponse>>
    fun getQuestionMetadata(examId: String): Flow<ApiResult<List<QuestionMetadataResponse>>>
}
