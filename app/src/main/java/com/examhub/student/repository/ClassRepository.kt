package com.examhub.student.repository

import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.classroom.JoinClassRequest
import com.examhub.student.model.response.classroom.MobileClassResponse
import com.examhub.student.model.response.common.PagedEnvelope
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
