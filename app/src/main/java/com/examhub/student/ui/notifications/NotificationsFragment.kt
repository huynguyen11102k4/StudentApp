package com.examhub.student.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentNotificationsBinding
import com.examhub.student.ui.applySystemWindowInsets
import com.examhub.student.ui.collectOnStarted
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModel()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_all_read -> {
                    viewModel.markAllAsRead()
                    Snackbar.make(binding.root, "Đã đánh dấu tất cả là đã đọc", Snackbar.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        adapter = NotificationAdapter(
            onItemClick = { notification ->
                viewModel.markAsRead(notification.id)
                handleNotificationClick(notification)
            },
            onMarkRead = { notification ->
                viewModel.markAsRead(notification.id)
            }
        )
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        collectOnStarted {
            launch {
                viewModel.notifications.collect { notifications ->
                    adapter.submitList(notifications)
                    binding.emptyState.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                    binding.swipeRefresh.visibility = if (notifications.isEmpty()) View.GONE else View.VISIBLE
                }
            }
            launch {
                viewModel.unreadCount.collect { unreadCount ->
                    binding.toolbar.subtitle = if (unreadCount > 0) "$unreadCount chưa đọc" else "Tất cả đã đọc"
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
            launch {
                viewModel.isRefreshing.collect { refreshing ->
                    binding.swipeRefresh.isRefreshing = refreshing
                }
            }
            launch {
                viewModel.errorMessage.collect { msg ->
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            }
        }
        viewModel.loadNotifications()
    }

    private fun handleNotificationClick(notification: com.examhub.student.data.model.AppNotification) {
        val type = notification.type.uppercase()
        val appealId = notification.appealId ?: notification.link?.extractLastId()
        val examId = notification.targetId
            ?: notification.entityId
            ?: notification.data?.stringValue("exam_id")
            ?: notification.data?.stringValue("examId")
            ?: notification.metadata?.stringValue("exam_id")
            ?: notification.metadata?.stringValue("examId")
            ?: notification.link?.extractLastId()
        if ((type == "APPEAL_CREATED" || type == "APPEAL_UPDATED" || type == "APPEAL_NEW") && !appealId.isNullOrBlank()) {
            val bundle = Bundle().apply { putString("appealId", appealId) }
            findNavController().navigate(R.id.action_notifications_to_appeal_detail, bundle)
            return
        }
        if ((type == "EXAM_CREATED" || type == "EXAM_OPENED" || type == "EXAM_UPCOMING" || type == "EXAM_REMINDER" || type == "EXAM_ASSIGNED") && !examId.isNullOrBlank()) {
            val bundle = Bundle().apply { putString("examId", examId) }
            findNavController().navigate(R.id.examDetailFragment, bundle)
            return
        }

        val link = notification.link.orEmpty()
        when {
            link.contains("appeals", ignoreCase = true) && !appealId.isNullOrBlank() -> {
                val bundle = Bundle().apply { putString("appealId", appealId) }
                findNavController().navigate(R.id.action_notifications_to_appeal_detail, bundle)
            }
            link.contains("appeals", ignoreCase = true) -> {
                findNavController().navigate(R.id.action_notifications_to_appeals_list)
            }
            link.contains("grading", ignoreCase = true) || link.contains("exams", ignoreCase = true) -> {
                findNavController().navigate(R.id.examListFragment)
            }
            else -> Unit
        }
    }

    private fun String.extractLastId(): String? {
        return split('/', '?', '#')
            .asReversed()
            .firstOrNull { it.length >= 16 && it.any(Char::isDigit) }
    }

    private fun com.google.gson.JsonObject.stringValue(key: String): String? {
        return get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
