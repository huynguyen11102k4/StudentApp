package com.examhub.student.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.FcmTokenRegistrar
import com.examhub.student.service.NotificationPreferenceManager
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val offlineCacheManager: OfflineCacheManager,
    private val notificationPreferenceManager: NotificationPreferenceManager,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val resources: ResourceProvider
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
            if (removed > 0) resources.getString(R.string.settings_storage_cleared_format, removed)
            else resources.getString(R.string.settings_storage_nothing_to_clear)
        )
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferenceManager.setEnabled(enabled)
        _notificationsEnabled.value = enabled
        if (enabled) {
            fcmTokenRegistrar.syncCurrentToken(viewModelScope)
            _errorMessage.tryEmit(resources.getString(R.string.settings_notifications_enabled))
        } else {
            _errorMessage.tryEmit(resources.getString(R.string.settings_notifications_disabled))
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (currentPassword.isBlank() || newPassword.length < 6 || currentPassword == newPassword) {
            _errorMessage.tryEmit(resources.getString(R.string.settings_change_password_invalid))
            return
        }
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
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.settings_change_password_failed))
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
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.settings_logout_failed))
                    }
                }
            }
        }
    }
}
