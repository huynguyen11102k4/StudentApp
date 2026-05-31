package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.exam.MobileExamDetailResponse
import com.examhub.student.model.response.exam.MobileExamSummaryResponse
import com.examhub.student.model.response.template.OmrTemplateResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.exam.QuestionMetadataResponse
import com.examhub.student.model.response.common.StartExamSessionResponse
import com.examhub.student.repository.ExamRepository
import com.examhub.student.service.ExamApiService
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
        gradingType: String?,
        search: String?
    ): Flow<ApiResult<PagedEnvelope<MobileExamSummaryResponse>>> =
        safeApiFlow(gson) { apiService.getExams(page, limit, status, excludeClosed, subject, gradingType, search) }

    override fun getExamDetail(examId: String): Flow<ApiResult<MobileExamDetailResponse>> =
        safeEnvelopeFlow(gson) { apiService.getExamDetail(examId) }

    override fun getExamTemplate(examId: String): Flow<ApiResult<OmrTemplateResponse>> =
        safeEnvelopeFlow(gson) { apiService.getExamTemplate(examId) }

    override fun startSession(examId: String): Flow<ApiResult<StartExamSessionResponse>> =
        safeEnvelopeFlow(gson) { apiService.startSession(examId) }

    override fun getQuestionMetadata(examId: String): Flow<ApiResult<List<QuestionMetadataResponse>>> =
        safeEnvelopeFlow(gson) { apiService.getQuestionMetadata(examId) }
}
