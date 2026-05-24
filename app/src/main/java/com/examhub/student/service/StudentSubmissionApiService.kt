package com.examhub.student.service

import com.examhub.student.model.request.submission.PresignSubmissionImageRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.submission.PresignSubmissionImageResponse
import com.examhub.student.model.response.submission.StudentSubmitResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface StudentSubmissionApiService {
    @POST("student/sessions/{sessionId}/submissions/presign")
    suspend fun presignImage(
        @Path("sessionId") sessionId: String,
        @Body request: PresignSubmissionImageRequest
    ): Response<ApiEnvelope<PresignSubmissionImageResponse>>

    @POST("student/sessions/{sessionId}/submit")
    suspend fun submit(
        @Path("sessionId") sessionId: String,
        @Body request: StudentSubmitRequest
    ): Response<ApiEnvelope<StudentSubmitResponse>>
}
