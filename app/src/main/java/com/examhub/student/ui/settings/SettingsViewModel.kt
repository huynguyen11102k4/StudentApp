package com.examhub.student.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.service.BackendUrlManager
import com.examhub.student.service.BackendUrlUpdateResult
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
    private val backendUrlManager: BackendUrlManager,
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

    private val _backendBaseUrl = MutableStateFlow(backendUrlManager.currentBaseUrl())
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _hasBackendOverride = MutableStateFlow(backendUrlManager.overrideBaseUrl() != null)
    val hasBackendOverride: StateFlow<Boolean> = _hasBackendOverride.asStateFlow()

    fun loadSettings() {
        _offlineExamCount.value = offlineCacheManager.getOfflineExamIds().size
        refreshBackendUrlState()
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

    fun saveBackendUrl(rawUrl: String) {
        when (backendUrlManager.saveOverride(rawUrl)) {
            BackendUrlUpdateResult.Changed -> {
                refreshBackendUrlState()
                _profile.value = null
                _sessionCount.value = 0
                _offlineExamCount.value = offlineCacheManager.getOfflineExamIds().size
                _errorMessage.tryEmit(resources.getString(R.string.settings_backend_url_saved_cleared))
            }
            BackendUrlUpdateResult.Unchanged -> {
                refreshBackendUrlState()
                _errorMessage.tryEmit(resources.getString(R.string.settings_backend_url_unchanged))
            }
            BackendUrlUpdateResult.Invalid -> {
                _errorMessage.tryEmit(resources.getString(R.string.settings_backend_url_invalid))
            }
        }
    }

    fun resetBackendUrl() {
        when (backendUrlManager.clearOverride()) {
            BackendUrlUpdateResult.Changed -> {
                refreshBackendUrlState()
                _profile.value = null
                _sessionCount.value = 0
                _offlineExamCount.value = offlineCacheManager.getOfflineExamIds().size
                _errorMessage.tryEmit(resources.getString(R.string.settings_backend_url_reset_cleared))
            }
            BackendUrlUpdateResult.Unchanged -> {
                refreshBackendUrlState()
                _errorMessage.tryEmit(resources.getString(R.string.settings_backend_url_reset_done))
            }
            BackendUrlUpdateResult.Invalid -> Unit
        }
    }

    private fun refreshBackendUrlState() {
        _backendBaseUrl.value = backendUrlManager.currentBaseUrl()
        _hasBackendOverride.value = backendUrlManager.overrideBaseUrl() != null
    }

    fun clearOfflineDownloads() {
        val removed = offlineCacheManager.clearOfflineDownloads()
        _offlineExamCount.value = offlineCacheManager.getOfflineExamIds().size
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
        val normalizedCurrent = currentPassword.trim()
        val normalizedNew = newPassword.trim()
        if (normalizedCurrent.isBlank() ||
            normalizedNew.isBlank() ||
            normalizedNew.length < 6 ||
            normalizedCurrent == normalizedNew
        ) {
            _errorMessage.tryEmit(resources.getString(R.string.settings_change_password_invalid))
            return
        }
        viewModelScope.launch {
            authRepository.changePassword(ChangePasswordRequest(normalizedCurrent, normalizedNew)).collect { result ->
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
