package com.examhub.student.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.examhub.student.R
import com.examhub.student.data.model.AppNotification
import com.examhub.student.databinding.FragmentNotificationsBinding
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.helper.NotificationNavigationHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModel()
    private lateinit var adapter: NotificationAdapter
    private var isLoading = false
    private var selectedFilter = NotificationsViewModel.NotificationFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appBar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_all_read -> {
                    viewModel.markAllAsRead()
                    Snackbar.make(binding.root, R.string.notifications_mark_all_read_success, Snackbar.LENGTH_SHORT).show()
                    true
                }
                R.id.action_clear_notifications -> {
                    viewModel.clearNotifications(adapter.snapshot().items.map { it.id })
                    Snackbar.make(binding.root, R.string.notifications_clear_success, Snackbar.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        adapter = NotificationAdapter(
            onItemClick = { notification ->
                viewModel.markAsRead(notification.id, wasUnread = !notification.isRead)
                handleNotificationClick(notification)
            },
            onMarkRead = { notification ->
                viewModel.markAsRead(notification.id, wasUnread = !notification.isRead)
            }
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
        binding.btnNotificationFilter.setOnClickListener { showFilterMenu() }

        binding.swipeRefresh.setOnRefreshListener {
            adapter.refresh()
        }

        collectOnStarted {
            launch {
                viewModel.notifications.collect { adapter.submitData(it) }
            }
            launch {
                viewModel.unreadCount.collect { unreadCount ->
                    binding.tvUnreadBadge.text = if (unreadCount > 0) {
                        resources.getQuantityString(R.plurals.notifications_unread_count, unreadCount, unreadCount)
                    } else {
                        getString(R.string.notifications_all_read)
                    }
                }
            }
            launch {
                adapter.loadStateFlow.collect { state ->
                    val loading = state.refresh is LoadState.Loading
                    isLoading = loading
                    binding.swipeRefresh.isRefreshing = loading
                    updateContentState(adapter.itemCount)
                }
            }
            launch { viewModel.refresh.collect { adapter.refresh() } }
            launch { viewModel.errorMessage.collect { msg -> Snackbar.make(binding.root, msg.replaceTechnicalLabels(), Snackbar.LENGTH_LONG).show() } }
        }
    }

    private fun handleNotificationClick(notification: AppNotification) {
        when (val destination = NotificationNavigationHelper.resolveDestination(notification)) {
            is NotificationNavigationHelper.Destination.AppealDetail -> {
                val bundle = Bundle().apply { putString("appealId", destination.appealId) }
                findNavController().navigate(R.id.action_notifications_to_appeal_detail, bundle)
            }
            is NotificationNavigationHelper.Destination.ResultDetail -> {
                val bundle = Bundle().apply { putString("sheetId", destination.sheetId) }
                findNavController().navigate(R.id.resultDetailFragment, bundle)
            }
            is NotificationNavigationHelper.Destination.ExamDetail -> {
                val bundle = Bundle().apply { putString("examId", destination.examId) }
                findNavController().navigate(R.id.examDetailFragment, bundle)
            }
            NotificationNavigationHelper.Destination.AppealsList -> {
                findNavController().navigate(R.id.action_notifications_to_appeals_list)
            }
            NotificationNavigationHelper.Destination.ResultsList -> {
                findNavController().navigate(R.id.resultsListFragment)
            }
            NotificationNavigationHelper.Destination.ExamList -> {
                findNavController().navigate(R.id.examListFragment)
            }
            NotificationNavigationHelper.Destination.Notifications,
            NotificationNavigationHelper.Destination.None -> Unit
        }
    }

    private fun updateContentState(itemCount: Int) {
        val showSkeleton = isLoading && itemCount == 0
        val showEmpty = !isLoading && itemCount == 0
        binding.loadingSkeleton.visibility = if (showSkeleton) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.swipeRefresh.visibility = if (showEmpty || showSkeleton) View.GONE else View.VISIBLE
    }

    private fun showFilterMenu() {
        PopupMenu(requireContext(), binding.btnNotificationFilter).apply {
            menu.add(0, MENU_FILTER_ALL, 0, R.string.notifications_filter_all)
            menu.add(0, MENU_FILTER_UNREAD, 1, R.string.notifications_filter_unread)
            menu.add(0, MENU_FILTER_READ, 2, R.string.notifications_filter_read)
            menu.setGroupCheckable(0, true, true)
            menu.findItem(menuIdForFilter(selectedFilter))?.isChecked = true
            setOnMenuItemClickListener { item ->
                selectedFilter = when (item.itemId) {
                    MENU_FILTER_UNREAD -> NotificationsViewModel.NotificationFilter.UNREAD
                    MENU_FILTER_READ -> NotificationsViewModel.NotificationFilter.READ
                    else -> NotificationsViewModel.NotificationFilter.ALL
                }
                viewModel.setFilter(selectedFilter)
                true
            }
            show()
        }
    }

    private fun menuIdForFilter(filter: NotificationsViewModel.NotificationFilter): Int {
        return when (filter) {
            NotificationsViewModel.NotificationFilter.UNREAD -> MENU_FILTER_UNREAD
            NotificationsViewModel.NotificationFilter.READ -> MENU_FILTER_READ
            NotificationsViewModel.NotificationFilter.ALL -> MENU_FILTER_ALL
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MENU_FILTER_ALL = 1
        const val MENU_FILTER_UNREAD = 2
        const val MENU_FILTER_READ = 3
    }
}
