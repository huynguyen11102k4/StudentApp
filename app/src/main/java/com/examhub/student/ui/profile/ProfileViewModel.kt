package com.examhub.student.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.GoogleLinkRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
import com.examhub.student.util.helper.sanitizedStudentProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val resources: ResourceProvider
) : ViewModel() {

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _successMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val successMessage: SharedFlow<String> = _successMessage.asSharedFlow()

    private val _userProfile = MutableStateFlow<UserResponse?>(null)
    val userProfile: StateFlow<UserResponse?> = _userProfile.asStateFlow()

    private val _hasPendingAvatar = MutableStateFlow(false)
    val hasPendingAvatar: StateFlow<Boolean> = _hasPendingAvatar.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            authRepository.getMe().collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isLoading.value = _userProfile.value == null
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _userProfile.value = result.data
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _errorMessage.tryEmit(
                            result.exception.message ?: resources.getString(R.string.profile_load_failed)
                        )
                    }
                }
            }
        }
    }

    fun setAvatarPending(pending: Boolean) {
        _hasPendingAvatar.value = pending
    }

    fun saveProfile(avatarFile: MultipartBody.Part?) {
        if (avatarFile == null) return
        viewModelScope.launch {
            _isSaving.value = true

            val latestProfile = when (val result = authRepository.uploadAvatar(avatarFile)
                .first { it !is ApiResult.Loading }) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> {
                    _isSaving.value = false
                    _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.profile_avatar_update_failed))
                    return@launch
                }
                else -> null
            }

            _isSaving.value = false
            _hasPendingAvatar.value = false
            latestProfile?.let { _userProfile.value = it }
            _saveSuccess.tryEmit(Unit)
        }
    }

    fun linkGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.login_error_google_token))
            return
        }
        viewModelScope.launch {
            authRepository.linkGoogle(GoogleLinkRequest(idToken)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isSaving.value = true
                    is ApiResult.Success -> {
                        _userProfile.value = _userProfile.value?.copy(
                            googleLinked = result.data.googleLinked,
                            hasPassword = result.data.hasPassword ?: _userProfile.value?.hasPassword,
                            authMethods = result.data.authMethods
                                ?: _userProfile.value?.authMethods?.copy(google = result.data.googleLinked)
                        )?.sanitizedStudentProfile()
                        refreshProfileAfterGoogleChange()
                        _isSaving.value = false
                        _successMessage.tryEmit(
                            if (result.data.updated == false) {
                                resources.getString(R.string.profile_google_linked_noop)
                            } else {
                                resources.getString(R.string.profile_google_linked_success)
                            }
                        )
                    }
                    is ApiResult.Error -> {
                        _isSaving.value = false
                        _errorMessage.tryEmit(
                            result.exception.googleErrorMessage(R.string.profile_google_link_failed)
                        )
                    }
                }
            }
        }
    }

    fun unlinkGoogle() {
        viewModelScope.launch {
            authRepository.unlinkGoogle().collect { result ->
                when (result) {
                    is ApiResult.Loading -> _isSaving.value = true
                    is ApiResult.Success -> {
                        _userProfile.value = _userProfile.value?.copy(
                            googleLinked = result.data.googleLinked,
                            hasPassword = result.data.hasPassword ?: _userProfile.value?.hasPassword,
                            authMethods = result.data.authMethods
                                ?: _userProfile.value?.authMethods?.copy(google = result.data.googleLinked)
                        )?.sanitizedStudentProfile()
                        refreshProfileAfterGoogleChange()
                        _isSaving.value = false
                        _successMessage.tryEmit(
                            if (result.data.updated == false) {
                                resources.getString(R.string.profile_google_unlinked_noop)
                            } else {
                                resources.getString(R.string.profile_google_unlinked_success)
                            }
                        )
                    }
                    is ApiResult.Error -> {
                        _isSaving.value = false
                        _errorMessage.tryEmit(
                            result.exception.googleErrorMessage(R.string.profile_google_unlink_failed)
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshProfileAfterGoogleChange() {
        authRepository.getMe().collect { refreshed ->
            if (refreshed is ApiResult.Success) {
                _userProfile.value = refreshed.data
            }
        }
    }

    private fun com.examhub.student.model.ApiException.googleErrorMessage(
        fallbackRes: Int
    ): String {
        val resource = when (code.uppercase()) {
            "INVALID_GOOGLE_TOKEN" -> R.string.auth_error_invalid_google_token
            "GOOGLE_EMAIL_MISMATCH" -> R.string.auth_error_google_email_mismatch
            "GOOGLE_ACCOUNT_ALREADY_LINKED" -> R.string.auth_error_google_already_linked
            "GOOGLE_ACCOUNT_MISMATCH" -> R.string.auth_error_google_account_mismatch
            "PASSWORD_REQUIRED_BEFORE_UNLINK" -> R.string.profile_google_unlink_password_required
            "ACCOUNT_INACTIVE" -> R.string.auth_error_account_inactive
            else -> null
        }
        return resource?.let(resources::getString)
            ?: message.takeIf(String::isNotBlank)
            ?: resources.getString(fallbackRes)
    }
}
