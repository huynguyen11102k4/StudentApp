package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.submission.PresignSubmissionImageRequest
import com.examhub.student.model.request.submission.StudentSubmitRequest
import com.examhub.student.model.response.submission.PresignSubmissionImageResponse
import com.examhub.student.model.response.submission.StudentSubmitResponse
import com.examhub.student.repository.StudentSubmissionRepository
import com.examhub.student.service.StudentSubmissionApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class StudentSubmissionRepositoryImpl(
    private val apiService: StudentSubmissionApiService,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient
) : StudentSubmissionRepository {
    override fun presignImage(
        sessionId: String,
        request: PresignSubmissionImageRequest,
        deviceId: String
    ): Flow<ApiResult<PresignSubmissionImageResponse>> =
        safeEnvelopeFlow(gson) { apiService.presignImage(sessionId, request, deviceId) }

    override fun uploadImage(uploadUrl: String, bytes: ByteArray, fileType: String): Flow<ApiResult<Unit>> = flow {
        emit(ApiResult.Loading)
        try {
            val request = Request.Builder()
                .url(uploadUrl)
                .put(bytes.toRequestBody(fileType.toMediaTypeOrNull()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    emit(ApiResult.Success(Unit))
                } else {
                    emit(
                        ApiResult.Error(
                            ApiException(
                                code = "UPLOAD_FAILED",
                                message = "Upload image failed (${response.code})",
                                httpCode = response.code
                            )
                        )
                    )
                }
            }
        } catch (error: IOException) {
            emit(
                ApiResult.Error(
                    ApiException(
                        code = "NETWORK_ERROR",
                        message = error.message ?: "Network error while uploading image",
                        causeThrowable = error
                    )
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    override fun submit(
        sessionId: String,
        request: StudentSubmitRequest,
        deviceId: String
    ): Flow<ApiResult<StudentSubmitResponse>> =
        safeEnvelopeFlow(gson) { apiService.submit(sessionId, request, deviceId) }
}
