package com.examhub.student.service

import com.examhub.student.model.request.classroom.JoinClassRequest
import com.examhub.student.model.response.common.ApiEnvelope
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.model.response.common.PagedEnvelope
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
