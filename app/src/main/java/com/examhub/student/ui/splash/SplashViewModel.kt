package com.examhub.student.ui.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.service.NetworkUtils
import com.examhub.student.service.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SplashDestination {
    data object Login : SplashDestination()
    data class Dashboard(val mode: String = "online") : SplashDestination()
}

class SplashViewModel(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination.asStateFlow()

    private val _statusText = MutableStateFlow("Dang khoi dong...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        viewModelScope.launch {
            delay(800)

            if (!tokenManager.hasToken()) {
                _statusText.value = "Chua dang nhap"
                delay(300)
                _destination.value = SplashDestination.Login
                return@launch
            }

            val hasNetwork = NetworkUtils.isNetworkAvailable(context)
            val hasRefreshToken = !tokenManager.getRefreshToken().isNullOrBlank()
            _statusText.value = if (hasNetwork) "Dang kiem tra phien..." else "Che do ngoai tuyen..."

            when {
                tokenManager.isTokenValid() && !tokenManager.isTokenExpiringSoon() -> {
                    _destination.value = SplashDestination.Dashboard(if (hasNetwork) "online" else "offline")
                }
                hasRefreshToken -> {
                    _statusText.value = if (hasNetwork) "Dang lam moi phien..." else "Che do ngoai tuyen..."
                    _destination.value = SplashDestination.Dashboard(if (hasNetwork) "online" else "offline-expired")
                }
                else -> {
                    tokenManager.clearTokens()
                    _statusText.value = "Phien dang nhap da het han"
                    delay(300)
                    _destination.value = SplashDestination.Login
                }
            }
        }
    }
}
