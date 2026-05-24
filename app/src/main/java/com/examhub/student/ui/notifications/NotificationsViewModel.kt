package com.examhub.student.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.examhub.student.data.model.AppNotification
import com.examhub.student.model.ApiResult
import com.examhub.student.repository.NotificationRepository
import com.examhub.student.service.OfflineCacheManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    fun loadNotifications() {
        viewModelScope.launch {
            val cached = offlineCacheManager.getCachedNotifications()
            if (cached.isNotEmpty()) {
                _notifications.value = cached
                _unreadCount.value = cached.count { !it.isRead }
            }

            notificationRepository.getNotifications().collect { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        if (!_isRefreshing.value) _isLoading.value = cached.isEmpty()
                    }
                    is ApiResult.Success -> {
                        _isLoading.value = false
                        _isRefreshing.value = false
                        val mapped = result.data.data.map { notif ->
                            AppNotification(
                                id = notif.id,
                                type = notif.type,
                                title = notif.title,
                                content = notif.content,
                                link = notif.link,
                                appealId = notif.appealId
                                    ?: notif.targetId
                                    ?: notif.entityId
                                    ?: notif.metadata.stringValue("appealId")
                                    ?: notif.metadata.stringValue("appeal_id")
                                    ?: notif.data.stringValue("appealId")
                                    ?: notif.data.stringValue("appeal_id"),
                                targetId = notif.targetId,
                                entityId = notif.entityId,
                                metadata = notif.metadata,
                                data = notif.data,
                                isRead = notif.isRead,
                                createdAt = notif.createdAt
                            )
                        }
                        _notifications.value = mapped
                        _unreadCount.value = mapped.count { !it.isRead }
                        offlineCacheManager.saveNotifications(mapped)
                        loadUnreadCount()
                    }
                    is ApiResult.Error -> {
                        _isLoading.value = false
                        _isRefreshing.value = false
                        _errorMessage.tryEmit(result.exception.message ?: "Không thể tải thông báo")
                    }
                }
            }
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        loadNotifications()
    }

    fun markAsRead(notificationId: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        _unreadCount.value = _notifications.value.count { !it.isRead }
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId).collect { result ->
                if (result is ApiResult.Error) {
                    _errorMessage.tryEmit(result.exception.message ?: "Không thể đánh dấu đã đọc")
                    loadNotifications()
                }
            }
        }
    }

    fun markAllAsRead() {
        if (_notifications.value.none { !it.isRead } && _unreadCount.value == 0) return

        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
        viewModelScope.launch {
            notificationRepository.markAllAsRead().collect { result ->
                when (result) {
                    is ApiResult.Success -> loadUnreadCount()
                    is ApiResult.Error -> {
                        _errorMessage.tryEmit(result.exception.message ?: "Không thể đánh dấu tất cả đã đọc")
                        loadNotifications()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun loadUnreadCount() {
        viewModelScope.launch {
            notificationRepository.getUnreadCount().collect { result ->
                if (result is ApiResult.Success) {
                    _unreadCount.value = result.data.count
                }
            }
        }
    }

    private fun com.google.gson.JsonObject?.stringValue(key: String): String? {
        return this?.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }
}
