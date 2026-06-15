package com.examhub.student.ui.profile

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.BuildConfig
import com.examhub.student.R
import com.examhub.student.databinding.FragmentProfileBinding
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.util.extension.applySystemWindowInsets
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.examhub.student.util.extension.showShimmer
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.util.Locale
import java.util.UUID

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
    }

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModel()
    private var googleSignInClient: GoogleSignInClient? = null
    private var pendingAvatarPart: MultipartBody.Part? = null
    private var pendingAvatarFile: File? = null
    private var hasProfile = false
    private var isSaving = false
    private val avatarMaxBytes = 5L * 1024L * 1024L
    private val vietnameseLocale = Locale.forLanguageTag("vi-VN")
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { prepareAvatar(it) }
    }
    private val takeAvatarLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { prepareAvatar(it) }
    }
    private val googleLinkLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    if (!idToken.isNullOrBlank()) {
                        viewModel.linkGoogle(idToken)
                    } else {
                        viewModel.linkGoogle("")
                    }
                } catch (e: ApiException) {
                    Log.e(TAG, "Google link failed: ${e.statusCode}", e)
                    Snackbar.make(binding.root, googleErrorMessage(e.statusCode), Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Snackbar.make(binding.root, R.string.login_error_google_failed, Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initGoogleSignIn()
        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.etFullName.isEnabled = false
        binding.etEmail.isEnabled = false
        binding.ivEditAvatar.setOnClickListener { showAvatarSourceDialog() }
        binding.btnGoogleLink.setOnClickListener {
            val profile = viewModel.userProfile.value
            if (profile.isGoogleLinked()) {
                if (profile?.hasPassword == false && profile.authMethods?.password != true) {
                    Snackbar.make(
                        binding.root,
                        R.string.profile_google_unlink_password_required,
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    confirmUnlinkGoogle()
                }
            } else {
                startGoogleLink()
            }
        }
        binding.btnSave.setOnClickListener {
            viewModel.saveProfile(pendingAvatarPart)
        }
        updateSaveButton()

        collectOnStarted {
            launch {
                viewModel.isSaving.collect { saving ->
                    isSaving = saving
                    binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
                    updateSaveButton()
                }
            }
            launch {
                viewModel.saveSuccess.collect {
                    pendingAvatarFile?.takeIf { it.exists() }?.delete()
                    pendingAvatarPart = null
                    pendingAvatarFile = null
                    viewModel.setAvatarPending(false)
                    Snackbar.make(binding.root, R.string.profile_saved, Snackbar.LENGTH_SHORT).show()
                    viewModel.userProfile.value?.let(::bindProfile)
                }
            }
            launch {
                viewModel.userProfile.collect { profile ->
                    hasProfile = profile != null
                    profile?.let(::bindProfile)
                    updateProfileLoadingState(viewModel.isLoading.value)
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    updateProfileLoadingState(loading)
                }
            }
            launch {
                viewModel.errorMessage.collect { message ->
                    Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.successMessage.collect { message ->
                    Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.hasPendingAvatar.collect {
                    updateSaveButton()
                }
            }
        }
        viewModel.loadProfile()
    }

    private fun initGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }

    private fun googleErrorMessage(statusCode: Int): String {
        return if (statusCode == 10) {
            getString(R.string.login_google_developer_error)
        } else {
            getString(R.string.login_google_failed_format, statusCode)
        }
    }

    private fun updateProfileLoadingState(loading: Boolean) {
        val showSkeleton = loading && !hasProfile
        binding.loadingSkeleton.root.showShimmer(showSkeleton)
        binding.contentScroll.visibility = if (showSkeleton) View.GONE else View.VISIBLE
    }

    private fun bindProfile(profile: UserResponse) {
        val rawName: String? = profile.fullName
        val rawRole: String? = profile.role
        val rawEmail: String? = profile.email
        val displayName = rawName.orEmpty().toTitleCaseName().ifBlank { getString(R.string.profile_name_placeholder) }
        binding.tvProfileDisplayName.text = displayName
        binding.tvProfileRole.text = rawRole.orEmpty()
            .ifBlank { getString(R.string.profile_role_student) }
            .uppercase(Locale.ROOT)
        binding.tvProfileStudentCodeTop.text = profile.student?.studentCode
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_student_code_top_empty)
        binding.etFullName.setText(displayName)
        binding.etEmail.setText(rawEmail.orEmpty())
        binding.btnGoogleLink.text = if (profile.isGoogleLinked()) {
            getString(R.string.profile_google_linked_action)
        } else {
            getString(R.string.profile_google_not_linked_action)
        }
        binding.tvStudentCode.text = profile.student?.studentCode
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.profile_student_code_format, it) }
            ?: getString(R.string.profile_student_code_empty)
        binding.tvPasswordStatus.text = when {
            profile.hasPassword == false -> getString(R.string.profile_password_not_set)
            profile.mustChangePassword == true -> getString(R.string.profile_password_must_change)
            else -> getString(R.string.profile_password_ok)
        }
        binding.tvCreatedAt.text = getString(
            R.string.profile_created_at_format,
            profile.createdAt?.toDisplayDateTime() ?: getString(R.string.common_not_updated)
        )

        if (pendingAvatarPart == null) {
            bindAvatar(profile.avatarUrl)
        }
        binding.tvProfileInitials.text = initials(displayName)
        updateSaveButton()
    }

    private fun updateSaveButton() {
        binding.btnSave.isEnabled = !isSaving && pendingAvatarPart != null
        binding.btnGoogleLink.isEnabled = !isSaving
    }

    private fun startGoogleLink() {
        val client = googleSignInClient
        if (client == null) {
            Snackbar.make(binding.root, R.string.login_google_not_configured, Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.btnGoogleLink.isEnabled = false
        client.signOut()
            .addOnCompleteListener {
                if (_binding == null) return@addOnCompleteListener
                binding.btnGoogleLink.isEnabled = true
                googleLinkLauncher.launch(client.signInIntent)
            }
    }

    private fun confirmUnlinkGoogle() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_google_unlink_title)
            .setMessage(R.string.profile_google_unlink_message)
            .setPositiveButton(R.string.profile_google_unlink_confirm) { _, _ ->
                viewModel.unlinkGoogle()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
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

    private fun showAvatarSourceDialog() {
        val options = arrayOf(
            getString(R.string.profile_avatar_source_gallery),
            getString(R.string.profile_avatar_source_camera)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_avatar_source_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickAvatarLauncher.launch("image/*")
                    1 -> takeAvatarLauncher.launch(null)
                }
            }
            .show()
    }

    private fun prepareAvatar(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
        if (mimeType !in setOf("image/jpeg", "image/png")) {
            Snackbar.make(binding.root, R.string.settings_avatar_unsupported, Snackbar.LENGTH_LONG).show()
            return
        }

        val extension = if (mimeType == "image/png") "png" else "jpg"
        val file = File(requireContext().cacheDir, "avatar-${UUID.randomUUID()}.$extension")
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error(getString(R.string.settings_avatar_read_failed))
        }.onFailure {
            Snackbar.make(binding.root, it.message ?: getString(R.string.settings_avatar_read_failed), Snackbar.LENGTH_LONG).show()
            return
        }

        prepareAvatarFile(file, mimeType)
    }

    private fun prepareAvatar(bitmap: Bitmap) {
        val file = File(requireContext().cacheDir, "avatar-${UUID.randomUUID()}.jpg")
        runCatching {
            file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output) }
        }.onFailure {
            Snackbar.make(binding.root, R.string.settings_avatar_read_failed, Snackbar.LENGTH_LONG).show()
            return
        }
        prepareAvatarFile(file, "image/jpeg")
    }

    private fun prepareAvatarFile(file: File, mimeType: String) {
        if (file.length() > avatarMaxBytes) {
            file.delete()
            Snackbar.make(binding.root, R.string.settings_avatar_too_large, Snackbar.LENGTH_LONG).show()
            return
        }

        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
        pendingAvatarFile?.takeIf { it.exists() }?.delete()
        pendingAvatarFile = file
        pendingAvatarPart = MultipartBody.Part.createFormData("file", file.name, body)
        viewModel.setAvatarPending(true)

        binding.ivProfileAvatar.visibility = View.VISIBLE
        binding.tvProfileInitials.visibility = View.GONE
        Glide.with(this)
            .load(file)
            .centerCrop()
            .into(binding.ivProfileAvatar)
        updateSaveButton()
    }

    private fun String.toDisplayDateTime(): String {
        return replace("T", " ")
            .removeSuffix("Z")
            .substringBefore(".")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingAvatarFile?.takeIf { it.exists() }?.delete()
        _binding = null
    }

    private fun UserResponse?.isGoogleLinked(): Boolean {
        return this?.googleLinked == true || this?.authMethods?.google == true
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .takeLast(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "HS" }
    }

    private fun String.toTitleCaseName(): String {
        return trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(vietnameseLocale)
            .split(" ")
            .joinToString(" ") { word ->
                word.split("-").joinToString("-") { part ->
                    part.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(vietnameseLocale) else char.toString()
                    }
                }
            }
    }
}
