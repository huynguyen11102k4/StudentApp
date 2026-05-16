package com.omr.scanner.student.service

import com.omr.scanner.student.model.request.JoinClassRequest
import com.omr.scanner.student.model.response.ApiEnvelope
import com.omr.scanner.student.model.response.MobileClassResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import retrofit2.http.Body
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface ClassApiService {
    @GET("student/classes")
    suspend fun getClasses(
        @Query("page") page: String = "1",
        @Query("limit") limit: String = "20",
        @Query("status") status: String? = null,
        @Query("schoolYear") schoolYear: String? = null
    ): Response<PagedEnvelope<MobileClassResponse>>

    @POST("student/classes/join")
    suspend fun joinClass(@Body request: JoinClassRequest): Response<ApiEnvelope<MobileClassResponse>>

    @GET("student/classes/{classId}")
    suspend fun getClassDetail(@Path("classId") classId: String): Response<ApiEnvelope<MobileClassResponse>>

}
