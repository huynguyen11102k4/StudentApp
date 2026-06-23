package com.examhub.student.ui.profile

import android.app.Application
import android.os.Looper
import com.examhub.student.model.ApiException
import com.examhub.student.model.ApiResult
import com.examhub.student.model.request.auth.ChangePasswordRequest
import com.examhub.student.model.request.auth.ForgotPasswordRequest
import com.examhub.student.model.request.auth.GoogleLinkRequest
import com.examhub.student.model.request.auth.GoogleLoginRequest
import com.examhub.student.model.request.auth.LoginRequest
import com.examhub.student.model.request.auth.OtpRequest
import com.examhub.student.model.request.auth.OtpVerifyRequest
import com.examhub.student.model.request.auth.ResetPasswordRequest
import com.examhub.student.model.request.auth.StudentRegisterRequest
import com.examhub.student.model.request.profile.UpdateProfileRequest
import com.examhub.student.model.response.auth.AuthTokenResponse
import com.examhub.student.model.response.auth.GoogleLinkResponse
import com.examhub.student.model.response.auth.OtpVerifyResponse
import com.examhub.student.model.response.auth.StudentRegisterResponse
import com.examhub.student.model.response.common.MessageResponse
import com.examhub.student.model.response.profile.MobileSessionResponse
import com.examhub.student.model.response.profile.StudentProfileResponse
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.repository.AuthRepository
import com.examhub.student.util.helper.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileViewModelTest {
    @Test
    fun avatarOnlySaveUploadsOnceWithoutUpdatingTextProfile() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        viewModel.setAvatarPending(true)

        viewModel.saveProfile(
            fullName = "Nguyen Van A",
            dateOfBirth = "2008-01-01",
            avatarFile = avatarPart(),
            updateTextProfile = false
        )
        idleMain()

        assertEquals(0, repository.updateCalls)
        assertEquals(1, repository.uploadCalls)
        assertFalse(viewModel.hasPendingAvatar.value)
    }

    @Test
    fun failedAvatarSaveKeepsPendingAvatarForRetry() {
        val repository = FakeAuthRepository(uploadResult = ApiResult.Error(ApiException("NETWORK_ERROR", "offline")))
        val viewModel = viewModel(repository)
        viewModel.setAvatarPending(true)

        viewModel.saveProfile(
            fullName = "Nguyen Van A",
            dateOfBirth = null,
            avatarFile = avatarPart(),
            updateTextProfile = false
        )
        idleMain()

        assertEquals(1, repository.uploadCalls)
        assertTrue(viewModel.hasPendingAvatar.value)
        assertFalse(viewModel.isSaving.value)
    }

    @Test
    fun profileSaveSendsNormalizedDob() {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)

        viewModel.saveProfile(
            fullName = "  Nguyen   Van   A  ",
            dateOfBirth = "2008-01-01T00:00:00.000Z",
            avatarFile = null,
            updateTextProfile = true
        )
        idleMain()

        assertEquals(UpdateProfileRequest("Nguyen Van A", "2008-01-01"), repository.lastUpdateRequest)
        assertEquals(1, repository.updateCalls)
        assertEquals(0, repository.uploadCalls)
    }

    private fun viewModel(repository: FakeAuthRepository): ProfileViewModel {
        return ProfileViewModel(repository, ResourceProvider(RuntimeEnvironment.getApplication()))
    }

    private fun avatarPart(): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file",
            "avatar.jpg",
            "avatar".toRequestBody("image/jpeg".toMediaType())
        )

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class FakeAuthRepository(
        private val uploadResult: ApiResult<UserResponse> = ApiResult.Success(profile("avatar.jpg")),
        private val updateResult: ApiResult<UserResponse> = ApiResult.Success(profile(dateOfBirth = "2008-01-01"))
    ) : AuthRepository {
        var updateCalls = 0
            private set
        var uploadCalls = 0
            private set
        var lastUpdateRequest: UpdateProfileRequest? = null
            private set

        override fun getMe(): Flow<ApiResult<UserResponse>> = flowOf(ApiResult.Success(profile()))

        override fun updateProfile(request: UpdateProfileRequest): Flow<ApiResult<UserResponse>> {
            updateCalls += 1
            lastUpdateRequest = request
            return flowOf(updateResult)
        }

        override fun uploadAvatar(file: MultipartBody.Part): Flow<ApiResult<UserResponse>> {
            uploadCalls += 1
            return flowOf(uploadResult)
        }

        override fun registerStudent(request: StudentRegisterRequest): Flow<ApiResult<StudentRegisterResponse>> = unused()
        override fun requestStudentOtp(request: OtpRequest): Flow<ApiResult<MessageResponse>> = unused()
        override fun verifyStudentOtp(request: OtpVerifyRequest): Flow<ApiResult<OtpVerifyResponse>> = unused()
        override fun login(request: LoginRequest): Flow<ApiResult<AuthTokenResponse>> = unused()
        override fun loginWithGoogle(request: GoogleLoginRequest): Flow<ApiResult<AuthTokenResponse>> = unused()
        override fun logout(): Flow<ApiResult<MessageResponse>> = unused()
        override fun changePassword(request: ChangePasswordRequest): Flow<ApiResult<MessageResponse>> = unused()
        override fun requestForgotPassword(request: ForgotPasswordRequest): Flow<ApiResult<MessageResponse>> = unused()
        override fun resetPassword(request: ResetPasswordRequest): Flow<ApiResult<MessageResponse>> = unused()
        override fun linkGoogle(request: GoogleLinkRequest): Flow<ApiResult<GoogleLinkResponse>> = unused()
        override fun unlinkGoogle(): Flow<ApiResult<GoogleLinkResponse>> = unused()
        override fun getSessions(): Flow<ApiResult<List<MobileSessionResponse>>> = unused()
        override fun revokeSession(sessionId: String): Flow<ApiResult<MessageResponse>> = unused()

        private fun <T> unused(): Flow<ApiResult<T>> = error("unused")
    }

    private companion object {
        fun profile(avatarUrl: String? = null, dateOfBirth: String? = null) = UserResponse(
            id = "user-1",
            email = "student@example.com",
            fullName = "Nguyen Van A",
            student = StudentProfileResponse(dateOfBirth = dateOfBirth),
            avatarUrl = avatarUrl
        )
    }
}
