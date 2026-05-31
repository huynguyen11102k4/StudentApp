package com.examhub.student.util.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.examhub.student.model.ApiResult
import com.examhub.student.model.response.common.PagedEnvelope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class RepositoryPagingSource<T : Any>(
    private val loadPage: suspend (page: Int, limit: Int) -> PageChunk<T>
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: 1
        return try {
            val chunk = loadPage(page, params.loadSize)
            LoadResult.Page(
                data = chunk.items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (page * chunk.limit >= chunk.total || chunk.items.isEmpty()) null else page + 1
            )
        } catch (error: Throwable) {
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.let { page ->
                page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
            }
        }
    }
}

data class PageChunk<T : Any>(
    val items: List<T>,
    val page: Int,
    val limit: Int,
    val total: Int
)

suspend fun <T : Any> Flow<ApiResult<PagedEnvelope<T>>>.requirePage(): PagedEnvelope<T> {
    return when (val result = first { it !is ApiResult.Loading }) {
        is ApiResult.Success -> result.data
        is ApiResult.Error -> throw result.exception
        ApiResult.Loading -> error("Unexpected loading state")
    }
}
