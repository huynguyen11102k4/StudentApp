package com.examhub.student.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.R
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.GoogleLinkRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
import com.examhub.student.util.helper.sanitizedStudentProfile
import com.examhub.student.util.helper.AuthErrorMapper
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

    private val _avatarUploadSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val avatarUploadSuccess: SharedFlow<Unit> = _avatarUploadSuccess.asSharedFlow()

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
                            AuthErrorMapper.message(
                                result.exception,
                                resources,
                                R.string.profile_load_failed
                            )
                        )
                    }
                }
            }
        }
    }

    fun setAvatarPending(pending: Boolean) {
        _hasPendingAvatar.value = pending
    }

    fun uploadAvatarOnly(avatarFile: MultipartBody.Part) {
        if (_isSaving.value) return
        viewModelScope.launch {
            setSaving(SAVE_AVATAR, true)

            val latestProfile = when (val result = authRepository.uploadAvatar(avatarFile)
                .first { it !is ApiResult.Loading }) {
                is ApiResult.Success -> result.data
                is ApiResult.Error -> {
                    setSaving(SAVE_AVATAR, false)
                    _errorMessage.tryEmit(
                        AuthErrorMapper.message(
                            result.exception,
                            resources,
                            R.string.profile_avatar_update_failed
                        )
                    )
                    return@launch
                }
                else -> null
            }

            _hasPendingAvatar.value = false
            setSaving(SAVE_AVATAR, false)
            latestProfile?.let { _userProfile.value = it }
            _avatarUploadSuccess.tryEmit(Unit)
        }
    }

    fun saveProfile(
        fullName: String,
        dateOfBirth: String?,
        avatarFile: MultipartBody.Part?,
        updateTextProfile: Boolean
    ) {
        if (_isSaving.value) return
        val normalizedName = fullName.trim().replace(Regex("\\s+"), " ")
        val normalizedDob = dateOfBirth?.trim()?.takeIf { it.isNotBlank() }?.substringBefore("T")
        if (normalizedName.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.profile_name_required))
            return
        }
        viewModelScope.launch {
            var latestProfile: UserResponse? = null
            setSaving(SAVE_PROFILE, updateTextProfile)
            setSaving(SAVE_AVATAR, avatarFile != null)
            if (updateTextProfile) {
                when (val profileResult = authRepository.updateProfile(
                    UpdateProfileRequest(
                        fullName = normalizedName,
                        dateOfBirth = normalizedDob
                    )
                ).first { it !is ApiResult.Loading }) {
                    is ApiResult.Success -> {
                        latestProfile = profileResult.data
                        _userProfile.value = profileResult.data
                    }
                    is ApiResult.Error -> {
                        setSaving(SAVE_PROFILE, false)
                        setSaving(SAVE_AVATAR, false)
                        _errorMessage.tryEmit(
                            AuthErrorMapper.message(
                                profileResult.exception,
                                resources,
                                R.string.profile_update_failed
                            )
                        )
                        return@launch
                    }
                    ApiResult.Loading -> Unit
                }
                setSaving(SAVE_PROFILE, false)
            }

            if (avatarFile != null) {
                when (val avatarResult = authRepository.uploadAvatar(avatarFile)
                    .first { it !is ApiResult.Loading }) {
                    is ApiResult.Success -> {
                        latestProfile = avatarResult.data
                        _userProfile.value = avatarResult.data
                        _hasPendingAvatar.value = false
                        _avatarUploadSuccess.tryEmit(Unit)
                    }
                    is ApiResult.Error -> {
                        setSaving(SAVE_AVATAR, false)
                        _errorMessage.tryEmit(
                            AuthErrorMapper.message(
                                avatarResult.exception,
                                resources,
                                R.string.profile_avatar_update_failed
                            )
                        )
                        return@launch
                    }
                    ApiResult.Loading -> Unit
                }
                setSaving(SAVE_AVATAR, false)
            }

            latestProfile?.let {
                _saveSuccess.tryEmit(Unit)
            }
        }
    }

    fun saveProfile(avatarFile: MultipartBody.Part?) {
        avatarFile?.let(::uploadAvatarOnly)
    }

    fun linkGoogle(idToken: String) {
        if (idToken.isBlank()) {
            _errorMessage.tryEmit(resources.getString(R.string.login_error_google_token))
            return
        }
        viewModelScope.launch {
            authRepository.linkGoogle(GoogleLinkRequest(idToken)).collect { result ->
                when (result) {
                    is ApiResult.Loading -> setSaving(SAVE_GOOGLE, true)
                    is ApiResult.Success -> {
                        _userProfile.value = _userProfile.value?.copy(
                            googleLinked = result.data.googleLinked,
                            hasPassword = result.data.hasPassword ?: _userProfile.value?.hasPassword,
                            authMethods = result.data.authMethods
                                ?: _userProfile.value?.authMethods?.copy(google = result.data.googleLinked)
                        )?.sanitizedStudentProfile()
                        refreshProfileAfterGoogleChange()
                        setSaving(SAVE_GOOGLE, false)
                        _successMessage.tryEmit(
                            if (result.data.updated == false) {
                                resources.getString(R.string.profile_google_linked_noop)
                            } else {
                                resources.getString(R.string.profile_google_linked_success)
                            }
                        )
                    }
                    is ApiResult.Error -> {
                        setSaving(SAVE_GOOGLE, false)
                        _errorMessage.tryEmit(
                            AuthErrorMapper.message(
                                result.exception,
                                resources,
                                R.string.profile_google_link_failed
                            )
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
                    is ApiResult.Loading -> setSaving(SAVE_GOOGLE, true)
                    is ApiResult.Success -> {
                        _userProfile.value = _userProfile.value?.copy(
                            googleLinked = result.data.googleLinked,
                            hasPassword = result.data.hasPassword ?: _userProfile.value?.hasPassword,
                            authMethods = result.data.authMethods
                                ?: _userProfile.value?.authMethods?.copy(google = result.data.googleLinked)
                        )?.sanitizedStudentProfile()
                        refreshProfileAfterGoogleChange()
                        setSaving(SAVE_GOOGLE, false)
                        _successMessage.tryEmit(
                            if (result.data.updated == false) {
                                resources.getString(R.string.profile_google_unlinked_noop)
                            } else {
                                resources.getString(R.string.profile_google_unlinked_success)
                            }
                        )
                    }
                    is ApiResult.Error -> {
                        setSaving(SAVE_GOOGLE, false)
                        _errorMessage.tryEmit(
                            AuthErrorMapper.message(
                                result.exception,
                                resources,
                                R.string.profile_google_unlink_failed
                            )
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

    private fun setSaving(operation: String, saving: Boolean) {
        if (saving) {
            activeSaveOperations.add(operation)
        } else {
            activeSaveOperations.remove(operation)
        }
        _isSaving.value = activeSaveOperations.isNotEmpty()
    }

    private val activeSaveOperations = mutableSetOf<String>()

    private companion object {
        const val SAVE_AVATAR = "avatar"
        const val SAVE_PROFILE = "profile"
        const val SAVE_GOOGLE = "google"
    }
}
