package com.examhub.student.repository_impl

import com.google.gson.Gson
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.classroom.JoinClassRequest
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.model.response.common.PagedEnvelope
import com.examhub.student.repository.ClassRepository
import com.examhub.student.service.ClassApiService
import kotlinx.coroutines.flow.Flow

class ClassRepositoryImpl(
    private val apiService: ClassApiService,
    private val gson: Gson
) : ClassRepository {
    override fun getClasses(
        page: String,
        limit: String,
        status: String?,
        schoolYear: String?,
        search: String?
    ): Flow<ApiResult<PagedEnvelope<MobileClassResponse>>> =
        safeApiFlow(gson) { apiService.getClasses(page, limit, status, schoolYear, search) }

    override fun joinClass(request: JoinClassRequest): Flow<ApiResult<MobileClassResponse>> =
        safeEnvelopeFlow(gson) { apiService.joinClass(request) }

    override fun getClassDetail(classId: String): Flow<ApiResult<MobileClassResponse>> =
        safeEnvelopeFlow(gson) { apiService.getClassDetail(classId) }
}
