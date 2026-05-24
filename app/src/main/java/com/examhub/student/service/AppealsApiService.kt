package com.examhub.student.service

import com.examhub.student.model.request.appeal.StudentAppealRequest
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.model.response.common.StudentAppealCreateResponse
import retrofit2.http.Body
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface AppealsApiService {
    @GET("student/appeals")
    suspend fun getAppeals(
        @Query("status") status: String? = null,
        @Query("examId") examId: String? = null,
        @Query("page") page: String = "1",
        @Query("limit") limit: String = "20"
    ): Response<PagedEnvelope<AppealSummaryResponse>>

    @GET("student/appeals/{appealId}")
    suspend fun getAppealDetail(@Path("appealId") appealId: String): Response<ApiEnvelope<AppealSummaryResponse>>

    @POST("student/appeals")
    suspend fun createAppeal(@Body request: StudentAppealRequest): Response<ApiEnvelope<StudentAppealCreateResponse>>

}
