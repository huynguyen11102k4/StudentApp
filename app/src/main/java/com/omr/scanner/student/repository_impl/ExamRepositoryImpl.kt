package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.response.MobileExamDetailResponse
import com.omr.scanner.student.model.response.MobileExamSummaryResponse
import com.omr.scanner.student.model.response.OmrTemplateResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.model.response.QuestionMetadataResponse
import com.omr.scanner.student.model.response.StartExamSessionResponse
import com.omr.scanner.student.repository.ExamRepository
import com.omr.scanner.student.service.ExamApiService
import kotlinx.coroutines.flow.Flow

class ExamRepositoryImpl(
    private val apiService: ExamApiService,
    private val gson: Gson
) : ExamRepository {
    override fun getExams(
        page: String,
        limit: String,
        status: String?,
        excludeClosed: Boolean?,
        subject: String?,
        gradingType: String?
    ): Flow<ApiResult<PagedEnvelope<MobileExamSummaryResponse>>> =
        safeApiFlow(gson) { apiService.getExams(page, limit, status, excludeClosed, subject, gradingType) }

    override fun getExamDetail(examId: String): Flow<ApiResult<MobileExamDetailResponse>> =
        safeEnvelopeFlow(gson) { apiService.getExamDetail(examId) }

    override fun getExamTemplate(examId: String): Flow<ApiResult<OmrTemplateResponse>> =
        safeEnvelopeFlow(gson) { apiService.getExamTemplate(examId) }

    override fun startSession(examId: String): Flow<ApiResult<StartExamSessionResponse>> =
        safeEnvelopeFlow(gson) { apiService.startSession(examId) }

    override fun getQuestionMetadata(examId: String): Flow<ApiResult<List<QuestionMetadataResponse>>> =
        safeEnvelopeFlow(gson) { apiService.getQuestionMetadata(examId) }
}
