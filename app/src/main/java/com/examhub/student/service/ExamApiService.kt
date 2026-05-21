package com.examhub.student.service

import com.examhub.student.model.response.ApiEnvelope
import com.examhub.student.model.response.MobileExamDetailResponse
import com.examhub.student.model.response.MobileExamSummaryResponse
import com.examhub.student.model.response.OmrTemplateResponse
import com.examhub.student.model.response.PagedEnvelope
import com.examhub.student.model.response.StartExamSessionResponse
import com.examhub.student.model.response.QuestionMetadataResponse
import retrofit2.http.POST
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ExamApiService {
    @GET("student/exams/upcoming")
    suspend fun getExams(
        @Query("page") page: String = "1",
        @Query("limit") limit: String = "20",
        @Query("status") status: String? = null,
        @Query("exclude_closed") excludeClosed: Boolean? = null,
        @Query("subject") subject: String? = null,
        @Query("grading_type") gradingType: String? = null
    ): Response<PagedEnvelope<MobileExamSummaryResponse>>

    @GET("student/exams/{examId}")
    suspend fun getExamDetail(@Path("examId") examId: String): Response<ApiEnvelope<MobileExamDetailResponse>>

    @GET("student/exams/{examId}/template")
    suspend fun getExamTemplate(@Path("examId") examId: String): Response<ApiEnvelope<OmrTemplateResponse>>

    @POST("student/exams/{examId}/start-session")
    suspend fun startSession(@Path("examId") examId: String): Response<ApiEnvelope<StartExamSessionResponse>>

    @GET("mobile/exams/{examId}/questions")
    suspend fun getQuestionMetadata(@Path("examId") examId: String): Response<ApiEnvelope<List<QuestionMetadataResponse>>>
}
