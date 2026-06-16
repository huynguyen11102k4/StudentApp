package com.examhub.student.repository_impl

import com.examhub.student.model.ApiResult
import com.examhub.student.service.StudentSubmissionApiService
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class StudentSubmissionRepositoryImplTest {
    @Test
    fun expiredPresignedUploadUrlIsReportedAsRetryable() = runBlocking {
        val repository = repositoryReturning(403)

        val result = repository.uploadImage("https://upload.test/file", byteArrayOf(1), "image/jpeg")
            .first { it !is ApiResult.Loading } as ApiResult.Error

        assertEquals("UPLOAD_URL_EXPIRED", result.exception.code)
    }

    @Test
    fun invalidUploadRequestRemainsTerminal() = runBlocking {
        val repository = repositoryReturning(400)

        val result = repository.uploadImage("https://upload.test/file", byteArrayOf(1), "image/jpeg")
            .first { it !is ApiResult.Loading } as ApiResult.Error

        assertEquals("UPLOAD_FAILED", result.exception.code)
    }

    private fun repositoryReturning(statusCode: Int): StudentSubmissionRepositoryImpl {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("test")
                .body(ByteArray(0).toResponseBody(null))
                .build()
        }
        return StudentSubmissionRepositoryImpl(
            apiService = unusedApiService(),
            gson = Gson(),
            okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        )
    }

    private fun unusedApiService(): StudentSubmissionApiService {
        return Proxy.newProxyInstance(
            StudentSubmissionApiService::class.java.classLoader,
            arrayOf(StudentSubmissionApiService::class.java)
        ) { _, method, _ ->
            error("Unexpected API call: ${method.name}")
        } as StudentSubmissionApiService
    }
}
