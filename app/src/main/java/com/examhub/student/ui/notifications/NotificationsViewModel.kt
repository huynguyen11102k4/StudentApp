package com.examhub.student.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.examhub.student.data.model.AppNotification
import com.examhub.student.model.ApiResult
import com.examhub.student.repository.NotificationRepository
import com.examhub.student.service.OfflineCacheManager
import com.examhub.student.util.paging.PageChunk
import com.examhub.student.util.paging.RepositoryPagingSource
import com.examhub.student.util.paging.requirePage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository,
    private val offlineCacheManager: OfflineCacheManager
) : ViewModel() {
    private val filter = MutableStateFlow(NotificationFilter.ALL)
    val notifications: Flow<PagingData<AppNotification>> = filter.flatMapLatest { selected ->
        Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 5, enablePlaceholders = false),
            pagingSourceFactory = {
                RepositoryPagingSource { page, limit ->
                    val response = notificationRepository.getNotifications(
                        page = page.toString(),
                        limit = limit.toString(),
                        unreadOnly = if (selected == NotificationFilter.UNREAD) true else null
                    ).requirePage()
                    val items = response.data.map(::toUiModel)
                    offlineCacheManager.saveNotifications(items)
                    _unreadCount.value = response.meta?.unreadCount ?: _unreadCount.value
                    PageChunk(items, response.meta?.page ?: page, response.meta?.limit ?: limit, response.meta?.total ?: items.size)
                }
            }
        ).flow.map { pagingData ->
            pagingData.filter { notification ->
                notification.id !in offlineCacheManager.getDismissedNotificationIds() &&
                    (selected != NotificationFilter.READ || notification.isRead)
            }
        }
    }.cachedIn(viewModelScope)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount = _unreadCount.asStateFlow()
    private val _errorMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    private val _refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refresh: SharedFlow<Unit> = _refresh.asSharedFlow()

    fun setFilter(value: NotificationFilter) {
        filter.value = value
    }

    fun markAsRead(notificationId: String, wasUnread: Boolean) {
        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId).collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        if (wasUnread) _unreadCount.value = (_unreadCount.value - 1).coerceAtLeast(0)
                        _refresh.tryEmit(Unit)
                    }
                    is ApiResult.Error -> _errorMessage.tryEmit(result.exception.message ?: "Không thể đánh dấu đã đọc")
                    ApiResult.Loading -> Unit
                }
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead().collect { result ->
                when (result) {
                    is ApiResult.Success -> {
                        _unreadCount.value = 0
                        _refresh.tryEmit(Unit)
                    }
                    is ApiResult.Error -> _errorMessage.tryEmit(result.exception.message ?: "Không thể đánh dấu đã đọc")
                    ApiResult.Loading -> Unit
                }
            }
        }
    }

    fun clearNotifications(ids: List<String>) {
        offlineCacheManager.dismissNotifications(ids)
        offlineCacheManager.clearNotifications()
        _refresh.tryEmit(Unit)
    }

    private fun toUiModel(notif: com.examhub.student.model.response.notification.NotificationResponse) = AppNotification(
        id = notif.id,
        type = notif.type,
        title = notif.title,
        content = notif.content,
        link = notif.link,
        appealId = notif.appealId ?: notif.targetId ?: notif.entityId
            ?: notif.metadata.stringValue("appealId") ?: notif.metadata.stringValue("appeal_id")
            ?: notif.data.stringValue("appealId") ?: notif.data.stringValue("appeal_id"),
        targetId = notif.targetId,
        entityId = notif.entityId,
        metadata = notif.metadata,
        data = notif.data,
        isRead = notif.isRead == true,
        createdAt = notif.createdAt.orEmpty()
    )

    private fun com.google.gson.JsonObject?.stringValue(key: String): String? =
        this?.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf(String::isNotBlank)

    enum class NotificationFilter { ALL, UNREAD, READ }
}
