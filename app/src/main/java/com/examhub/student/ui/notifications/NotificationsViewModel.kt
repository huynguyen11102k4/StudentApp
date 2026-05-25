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

    private var currentFilter = NotificationFilter.ALL
    private var allNotifications = emptyList<AppNotification>()

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
                allNotifications = cached
                applyFilter()
                _unreadCount.value = cached.count { !it.isRead }
            }

            notificationRepository.getNotifications(unreadOnly = unreadOnlyQuery()).collect { result ->
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
                                isRead = notif.isRead == true,
                                createdAt = notif.createdAt.orEmpty()
                            )
                        }
                        allNotifications = when (currentFilter) {
                            NotificationFilter.UNREAD -> mergeNotifications(
                                existing = allNotifications,
                                incoming = mapped
                            )
                            else -> mapped
                        }
                        applyFilter()
                        _unreadCount.value = result.data.meta?.unreadCount ?: mapped.count { !it.isRead }
                        offlineCacheManager.saveNotifications(allNotifications)
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

    fun setFilter(filter: NotificationFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        applyFilter()
        if (filter == NotificationFilter.UNREAD) {
            loadNotifications()
        }
    }

    fun markAsRead(notificationId: String) {
        allNotifications = allNotifications.map {
            if (it.id == notificationId) it.copy(isRead = true) else it
        }
        applyFilter()
        _unreadCount.value = allNotifications.count { !it.isRead }
        offlineCacheManager.saveNotifications(allNotifications)
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
        if (allNotifications.none { !it.isRead } && _unreadCount.value == 0) return

        allNotifications = allNotifications.map { it.copy(isRead = true) }
        applyFilter()
        _unreadCount.value = 0
        offlineCacheManager.saveNotifications(allNotifications)
        viewModelScope.launch {
            notificationRepository.markAllAsRead().collect { result ->
                when (result) {
                    is ApiResult.Success -> Unit
                    is ApiResult.Error -> {
                        _errorMessage.tryEmit(result.exception.message ?: "Không thể đánh dấu tất cả đã đọc")
                        loadNotifications()
                    }
                    else -> {}
                }
            }
        }
    }

    fun clearNotifications() {
        allNotifications = emptyList()
        _notifications.value = emptyList()
        _unreadCount.value = 0
        offlineCacheManager.clearNotifications()
    }

    private fun unreadOnlyQuery(): Boolean? {
        return if (currentFilter == NotificationFilter.UNREAD) true else null
    }

    private fun applyFilter() {
        _notifications.value = when (currentFilter) {
            NotificationFilter.ALL -> allNotifications
            NotificationFilter.UNREAD -> allNotifications.filter { !it.isRead }
            NotificationFilter.READ -> allNotifications.filter { it.isRead }
        }
    }

    private fun mergeNotifications(
        existing: List<AppNotification>,
        incoming: List<AppNotification>
    ): List<AppNotification> {
        val merged = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { merged[it.id] = it }
        return merged.values.sortedByDescending { it.createdAt }
    }

    private fun com.google.gson.JsonObject?.stringValue(key: String): String? {
        return this?.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
    }

    enum class NotificationFilter {
        ALL,
        UNREAD,
        READ
    }
}
