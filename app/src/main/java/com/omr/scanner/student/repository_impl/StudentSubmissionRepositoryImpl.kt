package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiException
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.PresignSubmissionImageRequest
import com.omr.scanner.student.model.request.StudentSubmitRequest
import com.omr.scanner.student.model.response.PresignSubmissionImageResponse
import com.omr.scanner.student.model.response.StudentSubmitResponse
import com.omr.scanner.student.repository.StudentSubmissionRepository
import com.omr.scanner.student.service.StudentSubmissionApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class StudentSubmissionRepositoryImpl(
    private val apiService: StudentSubmissionApiService,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient
) : StudentSubmissionRepository {
    override fun presignImage(
        sessionId: String,
        request: PresignSubmissionImageRequest
    ): Flow<ApiResult<PresignSubmissionImageResponse>> =
        safeEnvelopeFlow(gson) { apiService.presignImage(sessionId, request) }

    override fun uploadImage(uploadUrl: String, bytes: ByteArray, fileType: String): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        runCatching {
            val request = Request.Builder()
                .url(uploadUrl)
                .put(bytes.toRequestBody(fileType.toMediaTypeOrNull()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Upload anh that bai (${response.code})")
            }
        }.onSuccess {
            emit(ApiResult.Success(Unit))
        }.onFailure { error ->
            emit(ApiResult.Error(ApiException(code = "UPLOAD_FAILED", message = error.message ?: "Upload anh that bai")))
        }
    }

    override fun submit(
        sessionId: String,
        request: StudentSubmitRequest
    ): Flow<ApiResult<StudentSubmitResponse>> =
        safeEnvelopeFlow(gson) { apiService.submit(sessionId, request) }
}
