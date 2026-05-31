package com.examhub.student.ui.appeals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.examhub.student.data.model.Appeal
import com.examhub.student.model.response.appeal.AppealSummaryResponse
import com.examhub.student.repository.AppealsRepository
import com.examhub.student.util.extension.toFriendlyAppealItemStatus
import com.examhub.student.util.paging.PageChunk
import com.examhub.student.util.paging.RepositoryPagingSource
import com.examhub.student.util.paging.requirePage
import kotlinx.coroutines.flow.Flow

class AppealsListViewModel(private val appealsRepository: AppealsRepository) : ViewModel() {
    fun appeals(examId: String): Flow<PagingData<Appeal>> = Pager(
        config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false),
        pagingSourceFactory = {
            RepositoryPagingSource { page, limit ->
                val response = appealsRepository.getAppeals(
                    examId = examId.takeIf(String::isNotBlank),
                    page = page.toString(),
                    limit = limit.toString()
                ).requirePage()
                PageChunk(
                    items = response.data.map { it.toUiModel() },
                    page = response.meta?.page ?: page,
                    limit = response.meta?.limit ?: limit,
                    total = response.meta?.total ?: response.data.size
                )
            }
        }
    ).flow.cachedIn(viewModelScope)

    private fun AppealSummaryResponse.toUiModel(): Appeal {
        val resolvedExam = exam ?: sheet?.exam
        return Appeal(
            id = id,
            studentId = student?.id.orEmpty(),
            studentName = student?.name.orEmpty(),
            studentCode = student?.code.orEmpty(),
            examId = resolvedExam?.id.orEmpty(),
            examName = resolvedExam?.name.orEmpty(),
            subject = resolvedExam?.subject.orEmpty(),
            sheetId = sheet?.id.orEmpty(),
            oldScore = oldScore ?: sheet?.totalScore ?: 0.0,
            newScore = newScore,
            reason = reason.orEmpty(),
            status = status,
            createdAt = createdAt,
            teacherNote = teacherNote,
            processedImageUrl = sheet?.processedImageUrl,
            dewarpedImageUrl = sheet?.dewarpedImageUrl,
            itemMessages = items.orEmpty().joinToString("\n") { item ->
                "Câu ${item.questionNumber}: ${item.studentMessage.orEmpty().ifBlank { item.status.toFriendlyAppealItemStatus() }}"
            }
        )
    }
}
