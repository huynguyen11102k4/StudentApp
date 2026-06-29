package com.examhub.student.ui.settings

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSettingsBinding
import com.examhub.student.service.LanguagePreferenceManager
import com.examhub.student.service.ThemePreferenceManager
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.helper.UploadUrlResolver
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
        binding.itemBackendUrl.setOnClickListener {
            showBackendUrlDialog()
        }
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNotificationsEnabled(isChecked)
        }

        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = ThemePreferenceManager.isDarkModeApplied(requireContext())
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            ThemePreferenceManager.setDarkMode(requireContext(), isChecked)
        }
        bindLanguageValue()
        binding.itemLanguage.setOnClickListener {
            showLanguageDialog()
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
                        val fullName: String? = it.fullName
                        val email: String? = it.email
                        val role: String? = it.role
                        val displayName = fullName.orEmpty().ifBlank { getString(R.string.settings_default_student_name) }
                        binding.tvProfileName.text = displayName
                        binding.tvProfileEmail.text = email.orEmpty()
                        binding.tvProfileRole.text = role.orEmpty().ifBlank { getString(R.string.settings_default_student_role) }
                        binding.tvProfileInitials.text = initials(displayName)
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
                viewModel.backendBaseUrl.collect { url ->
                    binding.tvBackendUrlValue.text = url
                }
            }
            launch {
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.backendChangedMessage.collect { message ->
                    showBackendChangedDialog(message)
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

    private fun showBackendChangedDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_backend_url_changed_title)
            .setMessage(message.replaceTechnicalLabels())
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                findNavController().navigate(R.id.action_settings_to_login)
            }
            .setCancelable(false)
            .show()
    }

    private fun showLanguageDialog() {
        val tags = arrayOf(
            LanguagePreferenceManager.LANGUAGE_VI,
            LanguagePreferenceManager.LANGUAGE_EN
        )
        val labels = arrayOf(
            getString(R.string.settings_language_vietnamese),
            getString(R.string.settings_language_english)
        )
        val currentIndex = tags.indexOf(LanguagePreferenceManager.languageTag(requireContext()))
            .takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                LanguagePreferenceManager.setLanguage(requireContext(), tags[which])
                bindLanguageValue()
                Snackbar.make(binding.root, R.string.settings_language_changed, Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun bindLanguageValue() {
        binding.tvLanguageValue.text = when (LanguagePreferenceManager.languageTag(requireContext())) {
            LanguagePreferenceManager.LANGUAGE_EN -> getString(R.string.settings_language_english)
            else -> getString(R.string.settings_language_vietnamese)
        }
    }

    private fun showBackendUrlDialog() {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(viewModel.backendBaseUrl.value)
            setSelection(text?.length ?: 0)
        }
        val layout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.settings_backend_url_hint)
            addView(input)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.spacing_24)
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(layout)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_backend_url)
            .setView(container)
            .setNegativeButton(R.string.common_cancel, null)
            .setNeutralButton(R.string.settings_backend_url_reset) { _, _ ->
                viewModel.resetBackendUrl()
            }
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                viewModel.saveBackendUrl(input.text?.toString().orEmpty())
            }
            .show()
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
        val forgotPassword = TextView(requireContext()).apply {
            text = getString(R.string.profile_change_password_forgot)
            setTextColor(requireContext().getColor(R.color.primary))
            setPadding(0, resources.getDimensionPixelSize(R.dimen.spacing_12), 0, 0)
        }
        container.addView(currentPassword.layout)
        container.addView(newPassword.layout)
        container.addView(confirmPassword.layout)
        container.addView(forgotPassword)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_change_password)
            .setView(container)
            .setPositiveButton(R.string.common_confirm, null)
            .setNegativeButton(R.string.common_cancel, null)
            .show()
        forgotPassword.setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.forgotPasswordFragment)
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val current = currentPassword.editText.text?.toString().orEmpty()
            val new = newPassword.editText.text?.toString().orEmpty()
            val confirm = confirmPassword.editText.text?.toString().orEmpty()
            val isValid = validatePasswordDialog(currentPassword, newPassword, confirmPassword, current, new, confirm)
            if (isValid) {
                viewModel.changePassword(current.trim(), new.trim())
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
        val normalizedCurrent = current.trim()
        val normalizedNew = new.trim()
        val normalizedConfirm = confirm.trim()

        if (normalizedNew.isBlank()) {
            newPassword.layout.error = getString(R.string.login_validation_password_required)
            valid = false
        } else if (normalizedNew.length < 6) {
            newPassword.layout.error = getString(R.string.register_validation_password_short)
            valid = false
        } else if (normalizedNew == normalizedCurrent) {
            newPassword.layout.error = getString(R.string.settings_new_password_same)
            valid = false
        }
        if (normalizedConfirm != normalizedNew) {
            confirmPassword.layout.error = getString(R.string.register_validation_password_mismatch)
            valid = false
        }
        return valid
    }

    private fun bindAvatar(avatarUrl: String?) {
        val resolvedUrl = UploadUrlResolver.resolveUploadUrl(avatarUrl, viewModel.backendBaseUrl.value)
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
