package com.examhub.student.ui.classlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.SchoolClass
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.JoinClassRequest
import com.examhub.student.repository.ClassRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ClassListViewModel(
    private val classRepository: ClassRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {

    private val _classes = MutableStateFlow<List<SchoolClass>>(emptyList())
    val classes: StateFlow<List<SchoolClass>> = _classes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = _message.asSharedFlow()

    // true = joined successfully, false = blank code error, null = API error (with message in _message)
    private val _joinResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val joinResult: SharedFlow<Boolean> = _joinResult.asSharedFlow()

    fun loadClasses() {
        viewModelScope.launch {
            val cached = offlineCacheManager.getCachedClassBasics()
            if (cached.isNotEmpty()) _classes.value = cached

            classRepository.getClasses(status = "active").collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = cached.isEmpty()
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        val classes = result.data.data.map { cls ->
                            val info = cls.classInfo
                            SchoolClass(
                                id = cls.classId.ifBlank { info?.id ?: cls.id },
                                name = info?.className.orEmpty(),
                                subject = info?.subject ?: listOf(info?.grade, info?.schoolYear)
                                    .filterNotNull()
                                    .filter { it.isNotBlank() }
                                    .joinToString(" - "),
                                classCode = cls.resolvedInternalClassCode(),
                                joinCode = info?.joinCode.orEmpty(),
                                studentCount = cls.studentCount ?: info?.studentCount ?: 0,
                                hasOfflineData = false
                            )
                        }
                        offlineCacheManager.saveClassBasics(classes)
                        _classes.value = classes
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        if (_classes.value.isEmpty()) _classes.value = cached
                    }
                }
            }
        }
    }

    fun joinClass(joinCode: String) {
        val normalized = joinCode.trim()
        if (normalized.isBlank()) {
            _joinResult.tryEmit(false) // signal blank to Fragment, Fragment shows string
            return
        }

        viewModelScope.launch {
            classRepository.joinClass(JoinClassRequest(normalized)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _joinResult.tryEmit(true) // signal success to Fragment
                        loadClasses()
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: "")
                    }
                }
            }
        }
    }

    private fun com.examhub.student.model.response.MobileClassResponse.resolvedInternalClassCode(): String {
        return classInfo?.classCode.orEmpty()
    }

}
