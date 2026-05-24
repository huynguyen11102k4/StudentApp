package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.model.response.exam.MobileExamSummaryResponse
import com.examhub.student.model.response.template.OmrTemplateResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.exam.QuestionMetadataResponse
import com.examhub.student.model.response.common.StartExamSessionResponse
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
