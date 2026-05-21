package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.PresignSubmissionImageRequest
import com.examhub.student.model.request.StudentSubmitRequest
import com.examhub.student.model.response.PresignSubmissionImageResponse
import com.examhub.student.model.response.StudentSubmitResponse
import kotlinx.coroutines.flow.Flow

interface StudentSubmissionRepository {
    fun presignImage(sessionId: String, request: PresignSubmissionImageRequest): Flow<ApiResult<PresignSubmissionImageResponse>>
    fun uploadImage(uploadUrl: String, bytes: ByteArray, fileType: String): Flow<ApiResult<Unit>>
    fun submit(sessionId: String, request: StudentSubmitRequest): Flow<ApiResult<StudentSubmitResponse>>
}
