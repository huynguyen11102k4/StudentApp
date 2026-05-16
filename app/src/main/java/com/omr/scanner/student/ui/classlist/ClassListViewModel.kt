package com.omr.scanner.student.ui.classlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.data.model.SchoolClass
import com.omr.scanner.student.model.ApiResult
import com.omr.scanner.student.model.request.JoinClassRequest
import com.omr.scanner.student.repository.ClassRepository
import com.omr.scanner.student.service.OfflineCacheManager
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
                            SchoolClass(
                                id = cls.classId.ifBlank { cls.classInfo?.id ?: cls.id },
                                name = cls.className.ifBlank { cls.classInfo?.className.orEmpty() },
                                subject = cls.subject ?: cls.classInfo?.subject ?: listOf(cls.grade, cls.schoolYear)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" - "),
                                classCode = cls.internalId.orEmpty(),
                                joinCode = cls.joinCode.orEmpty(),
                                studentCount = cls.count?.classMembers ?: cls.studentCount,
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
            _message.tryEmit("Vui lòng nhập mã lớp")
            return
        }

        viewModelScope.launch {
            classRepository.joinClass(JoinClassRequest(normalized)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _message.tryEmit("Đã tham gia lớp")
                        loadClasses()
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _message.tryEmit(result.exception.message ?: "Không thể tham gia lớp")
                    }
                }
            }
        }
    }
}
