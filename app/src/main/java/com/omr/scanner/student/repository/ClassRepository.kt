package com.omr.scanner.student.repository

import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.JoinClassRequest
import com.omr.scanner.student.model.response.MobileClassResponse
import com.omr.scanner.student.model.response.PagedEnvelope
import kotlinx.coroutines.flow.Flow

interface ClassRepository {
    fun getClasses(
        page: String = "1",
        limit: String = "20",
        status: String? = null,
        schoolYear: String? = null
    ): Flow<ApiResult<PagedEnvelope<MobileClassResponse>>>
    fun joinClass(request: JoinClassRequest): Flow<ApiResult<MobileClassResponse>>
    fun getClassDetail(classId: String): Flow<ApiResult<MobileClassResponse>>
}
