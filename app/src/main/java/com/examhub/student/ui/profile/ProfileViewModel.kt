package com.examhub.student.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
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

    private val _saveSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private val _userProfile = MutableStateFlow<UserResponse?>(null)
    val userProfile: StateFlow<UserResponse?> = _userProfile.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            authRepository.getMe().collect { result ->
                when (result) {
                    is ApiResult.Loading -> Unit
                    is ApiResult.Success -> _userProfile.value = result.data
                    is ApiResult.Error -> _errorMessage.tryEmit(
                        result.exception.message ?: resources.getString(R.string.profile_load_failed)
                    )
                }
            }
        }
    }

    fun saveProfile(fullName: String, email: String, avatarFile: MultipartBody.Part?) {
        val currentProfile = _userProfile.value
        val currentEmail = currentProfile?.email.orEmpty()
        val normalizedName = fullName.trim()

        if (normalizedName.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.profile_name_required))
            return
        }
        if (email.trim() != currentEmail) {
            _errorMessage.tryEmit(resources.getString(R.string.profile_email_readonly))
            return
        }

        viewModelScope.launch {
            _isSaving.value = true

            var latestProfile = currentProfile
            if (normalizedName != currentProfile?.fullName) {
                when (val result = authRepository.updateProfile(UpdateProfileRequest(normalizedName))
                    .first { it !is ApiResult.Loading }) {
                    is ApiResult.Success -> latestProfile = result.data
                    is ApiResult.Error -> {
                        _isSaving.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.profile_update_failed))
                        return@launch
                    }
                    else -> Unit
                }
            }

            if (avatarFile != null) {
                when (val result = authRepository.uploadAvatar(avatarFile)
                    .first { it !is ApiResult.Loading }) {
                    is ApiResult.Success -> latestProfile = result.data
                    is ApiResult.Error -> {
                        _isSaving.value = false
                        _errorMessage.tryEmit(result.exception.message ?: resources.getString(R.string.profile_avatar_update_failed))
                        return@launch
                    }
                    else -> Unit
                }
            }

            _isSaving.value = false
            latestProfile?.let { _userProfile.value = it }
            _saveSuccess.tryEmit(Unit)
        }
    }
}
