package com.omr.scanner.student.repository_impl

import com.google.gson.Gson
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.JoinClassRequest
import com.omr.scanner.student.model.response.MobileClassResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import com.omr.scanner.student.repository.ClassRepository
import com.omr.scanner.student.service.ClassApiService
import kotlinx.coroutines.flow.Flow

class ClassRepositoryImpl(
    private val apiService: ClassApiService,
    private val gson: Gson
) : ClassRepository {
    override fun getClasses(
        page: String,
        limit: String,
        status: String?,
        schoolYear: String?
    ): Flow<ApiResult<PagedEnvelope<MobileClassResponse>>> =
        safeApiFlow(gson) { apiService.getClasses(page, limit, status, schoolYear) }

    override fun joinClass(request: JoinClassRequest): Flow<ApiResult<MobileClassResponse>> =
        safeEnvelopeFlow(gson) { apiService.joinClass(request) }

    override fun getClassDetail(classId: String): Flow<ApiResult<MobileClassResponse>> =
        safeEnvelopeFlow(gson) { apiService.getClassDetail(classId) }
}
