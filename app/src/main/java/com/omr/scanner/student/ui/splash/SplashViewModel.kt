package com.omr.scanner.student.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omr.scanner.student.service.NetworkUtils
import com.omr.scanner.student.service.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SplashDestination {
    data object Login : SplashDestination()
    data class Dashboard(val mode: String = "online") : SplashDestination()
}

class SplashViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _destination = MutableSharedFlow<SplashDestination>(extraBufferCapacity = 1)
    val destination: SharedFlow<SplashDestination> = _destination.asSharedFlow()

    private val _statusText = MutableStateFlow("Đang khởi động...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun start(context: android.content.Context) {
        viewModelScope.launch {
            // Minimum splash display time for UX
            delay(800)

            if (!tokenManager.hasToken()) {
                _statusText.value = "Chưa đăng nhập"
                delay(300)
                _destination.emit(SplashDestination.Login)
                return@launch
            }

            val hasNetwork = NetworkUtils.isNetworkAvailable(context)
            _statusText.value = if (hasNetwork) "Đang kiểm tra phiên..." else "Chế độ ngoại tuyến..."

            if (tokenManager.isTokenValid() && !tokenManager.isTokenExpiringSoon()) {
                // Token còn hạn
                if (hasNetwork) {
                    // Optional: verify with /auth/me — skip for speed, just go to dashboard
                    _destination.emit(SplashDestination.Dashboard("online"))
                } else {
                    _destination.emit(SplashDestination.Dashboard("offline"))
                }
            } else if (tokenManager.isTokenExpiringSoon() || !tokenManager.isTokenValid()) {
                // Token hết hạn hoặc sắp hết hạn
                if (hasNetwork) {
                    // Try refresh — but we can't easily call RefreshTokenService from ViewModel
                    // The fragment will handle this by navigating to Dashboard where the
                    // AuthInterceptor + TokenAuthenticator will auto-refresh on first API call.
                    _statusText.value = "Đang làm mới phiên..."
                    _destination.emit(SplashDestination.Dashboard("online"))
                } else {
                    // Offline with expired token — go to dashboard read-only
                    _destination.emit(SplashDestination.Dashboard("offline-expired"))
                }
            } else {
                _destination.emit(SplashDestination.Login)
            }
        }
    }
}
