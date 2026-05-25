package com.examhub.student.ui.settings

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.BuildConfig
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSettingsBinding
import com.examhub.student.service.ThemePreferenceManager
import com.examhub.student.extension.applySystemWindowInsets
import com.examhub.student.extension.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.util.UUID

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.itemAccount.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_profile)
        }
        binding.itemChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        binding.itemStorage.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_storage_cleanup_title)
                .setMessage(R.string.settings_storage_cleanup_message)
                .setPositiveButton(R.string.settings_storage_cleanup_confirm) { _, _ ->
                    viewModel.clearOfflineDownloads()
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNotificationsEnabled(isChecked)
        }

        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = ThemePreferenceManager.isDarkModeApplied(requireContext())
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            ThemePreferenceManager.setDarkMode(requireContext(), isChecked)
        }

        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_logout_confirm_title)
                .setMessage(R.string.settings_logout_confirm_message)
                .setPositiveButton(R.string.settings_logout_confirm_yes) { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton(R.string.settings_logout_confirm_no, null)
                .show()
        }

        collectOnStarted {
            launch {
                viewModel.profile.collect { profile ->
                    profile?.let {
                        binding.tvProfileName.text = it.fullName
                        binding.tvProfileEmail.text = it.email
                        binding.tvProfileRole.text = it.role
                        binding.tvProfileInitials.text = initials(it.fullName)
                        bindAvatar(it.avatarUrl)
                    }
                }
            }
            launch {
                viewModel.sessionCount.collect { count ->
                    binding.tvSessionCount.text = resources.getQuantityString(R.plurals.settings_session_count, count, count)
                }
            }
            launch {
                viewModel.logoutSuccess.collect {
                    findNavController().navigate(R.id.action_settings_to_login)
                }
            }
            launch {
                viewModel.offlineExamCount.collect { count ->
                    binding.itemStorage.contentDescription = resources.getQuantityString(
                        R.plurals.settings_storage_content_description,
                        count,
                        count
                    )
                }
            }
            launch {
                viewModel.notificationsEnabled.collect { enabled ->
                    if (binding.switchNotifications.isChecked != enabled) {
                        binding.switchNotifications.setOnCheckedChangeListener(null)
                        binding.switchNotifications.isChecked = enabled
                        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
                            viewModel.setNotificationsEnabled(isChecked)
                        }
                    }
                }
            }
            launch {
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.btnLogout.isEnabled = !loading
                    binding.itemChangePassword.isEnabled = !loading
                }
            }
        }
        viewModel.loadSettings()
    }

    private fun showChangePasswordDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.spacing_24)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
        }
        val currentPassword = passwordField(R.string.settings_current_password_hint)
        val newPassword = passwordField(R.string.settings_new_password_hint)
        val confirmPassword = passwordField(R.string.settings_confirm_password_hint)
        container.addView(currentPassword.layout)
        container.addView(newPassword.layout)
        container.addView(confirmPassword.layout)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_change_password)
            .setView(container)
            .setPositiveButton(R.string.common_confirm, null)
            .setNegativeButton(R.string.common_cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val current = currentPassword.editText.text?.toString().orEmpty()
            val new = newPassword.editText.text?.toString().orEmpty()
            val confirm = confirmPassword.editText.text?.toString().orEmpty()
            val isValid = validatePasswordDialog(currentPassword, newPassword, confirmPassword, current, new, confirm)
            if (isValid) {
                viewModel.changePassword(current, new)
                dialog.dismiss()
            }
        }
    }

    private fun passwordField(hintRes: Int): PasswordField {
        val editText = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val layout = TextInputLayout(requireContext()).apply {
            hint = getString(hintRes)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            addView(editText)
        }
        return PasswordField(layout, editText)
    }

    private fun validatePasswordDialog(
        currentPassword: PasswordField,
        newPassword: PasswordField,
        confirmPassword: PasswordField,
        current: String,
        new: String,
        confirm: String
    ): Boolean {
        currentPassword.layout.error = null
        newPassword.layout.error = null
        confirmPassword.layout.error = null

        var valid = true
        if (current.isBlank()) {
            currentPassword.layout.error = getString(R.string.settings_current_password_required)
            valid = false
        }
        if (new.length < 6) {
            newPassword.layout.error = getString(R.string.register_validation_password_short)
            valid = false
        } else if (new == current) {
            newPassword.layout.error = getString(R.string.settings_new_password_same)
            valid = false
        }
        if (confirm != new) {
            confirmPassword.layout.error = getString(R.string.register_validation_password_mismatch)
            valid = false
        }
        return valid
    }

    private fun bindAvatar(avatarUrl: String?) {
        val resolvedUrl = avatarUrl?.takeIf { it.isNotBlank() }?.let(::resolveAvatarUrl)
        if (resolvedUrl == null) {
            binding.ivProfileAvatar.visibility = View.GONE
            binding.tvProfileInitials.visibility = View.VISIBLE
            binding.ivProfileAvatar.setImageDrawable(null)
            return
        }

        binding.ivProfileAvatar.visibility = View.VISIBLE
        binding.tvProfileInitials.visibility = View.GONE
        Glide.with(this)
            .load(resolvedUrl)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .centerCrop()
            .error(R.drawable.bg_avatar)
            .into(binding.ivProfileAvatar)
    }

    private fun resolveAvatarUrl(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val apiBase = BuildConfig.API_BASE_URL.trimEnd('/')
        val origin = apiBase.substringBefore("/api/v1")
        return origin + if (url.startsWith("/")) url else "/$url"
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .takeLast(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "HS" }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private data class PasswordField(
    val layout: TextInputLayout,
    val editText: TextInputEditText
)
