package com.examhub.student.ui.classlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.examhub.student.R
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.classroom.JoinClassRequest
import com.examhub.student.repository.AuthRepository
import com.examhub.student.repository.ClassRepository
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.util.helper.ResourceProvider
import com.examhub.student.util.paging.PageChunk
import com.examhub.student.util.paging.RepositoryPagingSource
import com.examhub.student.util.paging.requirePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class ClassListViewModel(
    private val classRepository: ClassRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val authRepository: AuthRepository,
    private val resources: ResourceProvider
) : ViewModel() {
    private val search = MutableStateFlow("")
    val classes: Flow<PagingData<SchoolClass>> = search
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false),
                pagingSourceFactory = {
                    RepositoryPagingSource { page, limit ->
                        runCatching {
                            val response = classRepository.getClasses(
                                page = page.toString(),
                                limit = limit.toString(),
                                status = "active",
                                search = query.takeIf(String::isNotBlank)
                            ).requirePage()
                            val items = response.data.map { cls ->
                                val info = cls.classInfo
                                SchoolClass(
                                    id = cls.classId.ifBlank { info?.id ?: cls.id },
                                    name = info?.className.orEmpty(),
                                    subject = info?.subject ?: listOf(info?.grade, info?.schoolYear)
                                        .filterNotNull().filter(String::isNotBlank).joinToString(" - "),
                                    classCode = info?.classCode.orEmpty(),
                                    joinCode = info?.joinCode.orEmpty(),
                                    studentCount = cls.studentCount ?: info?.studentCount ?: 0,
                                    status = cls.status,
                                    hasOfflineData = false
                                )
                            }
                            offlineCacheManager.saveClassBasics(
                                items,
                                replaceExisting = page == 1 && query.isBlank()
                            )
                            PageChunk(
                                items,
                                response.meta?.page ?: page,
                                response.meta?.limit ?: limit,
                                response.meta?.total ?: items.size
                            )
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            cachedClassPage(query, page, limit)
                        }
                    }
                }
            ).flow
        }
        .cachedIn(viewModelScope)

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()
    private val _defaultStudentCode = MutableStateFlow("")
    val defaultStudentCode = _defaultStudentCode
    private val _joinResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val joinResult: SharedFlow<Boolean> = _joinResult.asSharedFlow()

    init {
        loadDefaultStudentCode()
    }

    fun setSearch(query: String) {
        search.value = query.trim()
    }

    fun joinClass(joinCode: String, studentCode: String) {
        val normalized = joinCode.trim()
        val normalizedStudentCode = studentCode.trim()
        if (normalized.isBlank()) {
            _joinResult.tryEmit(false)
            return
        }
        if (normalizedStudentCode.isBlank()) {
            _message.tryEmit(resources.getString(R.string.class_list_student_code_empty))
            return
        }
        viewModelScope.launch {
            classRepository.joinClass(JoinClassRequest(normalized, normalizedStudentCode)).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _defaultStudentCode.value = normalizedStudentCode
                        _joinResult.tryEmit(true)
                        loadDefaultStudentCode()
                    }
                    is ApiResult.Error -> _message.tryEmit(
                        if (result.exception.code == "STUDENT_CODE_EXISTS_IN_CLASS") {
                            resources.getString(R.string.class_list_student_code_exists)
                        } else {
                            result.exception.message.orEmpty()
                        }
                    )
                    ApiResult.Loading -> Unit
                }
            }
        }
    }

    private fun loadDefaultStudentCode() {
        viewModelScope.launch {
            authRepository.getMe().collect { result ->
                if (result is ApiResult.Success) _defaultStudentCode.value = result.data.student?.studentCode.orEmpty()
            }
        }
    }

    private fun cachedClassPage(query: String, page: Int, limit: Int): PageChunk<SchoolClass> {
        val normalizedQuery = query.trim()
        val filtered = offlineCacheManager.getCachedClassBasics().filter { cls ->
            normalizedQuery.isBlank() ||
                cls.name.contains(normalizedQuery, ignoreCase = true) ||
                cls.subject.contains(normalizedQuery, ignoreCase = true) ||
                cls.classCode.contains(normalizedQuery, ignoreCase = true) ||
                cls.joinCode.contains(normalizedQuery, ignoreCase = true)
        }
        val fromIndex = ((page - 1) * limit).coerceAtLeast(0)
        val pageItems = if (fromIndex >= filtered.size) {
            emptyList()
        } else {
            filtered.drop(fromIndex).take(limit)
        }
        return PageChunk(pageItems, page, limit, filtered.size)
    }
}
