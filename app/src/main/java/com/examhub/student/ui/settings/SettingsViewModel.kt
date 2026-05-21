package com.examhub.student.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.ChangePasswordRequest
import com.examhub.student.model.response.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.service.NotificationPreferenceManager
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val notificationPreferenceManager: NotificationPreferenceManager,
    private val fcmTokenRegistrar: FcmTokenRegistrar
) : ViewModel() {

    private val _logoutSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutSuccess: SharedFlow<Unit> = _logoutSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _profile = MutableStateFlow<UserResponse?>(null)
    val profile: StateFlow<UserResponse?> = _profile.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _offlineExamCount = MutableStateFlow(0)
    val offlineExamCount: StateFlow<Int> = _offlineExamCount.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(notificationPreferenceManager.isEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun loadSettings() {
        _offlineExamCount.value = offlineCacheManager.getOfflineExamIds().size
        viewModelScope.launch {
            authRepository.getMe().collect { result ->
                if (result is ApiResult.Success) _profile.value = result.data
            }
        }
        viewModelScope.launch {
            authRepository.getSessions().collect { result ->
                if (result is ApiResult.Success) _sessionCount.value = result.data.size
            }
        }
    }

    fun clearOfflineDownloads() {
        val removed = offlineCacheManager.clearOfflineDownloads()
        _offlineExamCount.value = 0
        _errorMessage.tryEmit(
            if (removed > 0) "Đã xóa mẫu OMR của $removed kỳ thi"
            else "Không có mẫu OMR để xóa"
        )
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferenceManager.setEnabled(enabled)
        _notificationsEnabled.value = enabled
        if (enabled) {
            fcmTokenRegistrar.syncCurrentToken(viewModelScope)
            _errorMessage.tryEmit("Đã bật thông báo")
        } else {
            _errorMessage.tryEmit("Đã tắt thông báo trên thiết bị này")
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            authRepository.changePassword(ChangePasswordRequest(currentPassword, newPassword)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.data.message)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Không thể đổi mật khẩu")
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout().collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = true
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _logoutSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Đăng xuất thất bại")
                    }
                }
            }
        }
    }
}
