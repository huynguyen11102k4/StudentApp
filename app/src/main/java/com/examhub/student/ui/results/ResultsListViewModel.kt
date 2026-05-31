package com.examhub.student.ui.results

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.repository.ResultsRepository
import com.examhub.student.util.paging.PageChunk
import com.examhub.student.util.paging.RepositoryPagingSource
import com.examhub.student.util.paging.requirePage

class ResultsListViewModel(
    private val resultsRepository: ResultsRepository
) : ViewModel() {
    val results = Pager(
        config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false),
        pagingSourceFactory = {
            RepositoryPagingSource { page, limit ->
                val response = resultsRepository.getResults(page.toString(), limit.toString()).requirePage()
                val meta = response.meta
                PageChunk(response.data, meta?.page ?: page, meta?.limit ?: limit, meta?.total ?: response.data.size)
            }
        }
    ).flow.cachedIn(viewModelScope)
}
