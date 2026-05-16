package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.PresignSubmissionImageRequest
import com.omr.scanner.student.model.request.StudentSubmitRequest
import com.omr.scanner.student.model.response.PresignSubmissionImageResponse
import com.omr.scanner.student.model.response.StudentSubmitResponse
import kotlinx.coroutines.flow.Flow

interface StudentSubmissionRepository {
    fun presignImage(sessionId: String, request: PresignSubmissionImageRequest): Flow<ApiResult<PresignSubmissionImageResponse>>
    fun uploadImage(uploadUrl: String, bytes: ByteArray, fileType: String): Flow<ApiResult<Unit>>
    fun submit(sessionId: String, request: StudentSubmitRequest): Flow<ApiResult<StudentSubmitResponse>>
}
