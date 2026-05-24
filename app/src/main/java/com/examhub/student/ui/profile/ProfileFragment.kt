package com.examhub.student.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.BuildConfig
import com.examhub.student.R
import com.examhub.student.databinding.FragmentProfileBinding
import com.examhub.student.model.response.profile.UserResponse
import com.examhub.student.ui.applySystemWindowInsets
import com.examhub.student.ui.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.util.UUID

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModel()
    private var pendingAvatarPart: MultipartBody.Part? = null
    private var pendingAvatarFile: File? = null
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { prepareAvatar(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.applySystemWindowInsets(top = true)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.ivEditAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        binding.btnSave.setOnClickListener {
            viewModel.saveProfile(
                binding.etFullName.text.toString(),
                binding.etEmail.text.toString(),
                pendingAvatarPart
            )
        }

        collectOnStarted {
            launch {
                viewModel.isSaving.collect { saving ->
                    binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
                    binding.btnSave.isEnabled = !saving
                }
            }
            launch {
                viewModel.saveSuccess.collect {
                    pendingAvatarPart = null
                    pendingAvatarFile = null
                    Snackbar.make(binding.root, R.string.profile_saved, Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
            }
            launch {
                viewModel.userProfile.collect { profile ->
                    profile?.let(::bindProfile)
                }
            }
            launch {
                viewModel.errorMessage.collect { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        viewModel.loadProfile()
    }

    private fun bindProfile(profile: UserResponse) {
        binding.tvProfileDisplayName.text = profile.fullName
        binding.tvProfileRole.text = profile.role
        binding.etFullName.setText(profile.fullName)
        binding.etEmail.setText(profile.email)
        binding.tvGoogleStatus.text = if (profile.googleId.isNullOrBlank()) {
            getString(R.string.profile_google_not_linked)
        } else {
            getString(R.string.profile_google_linked)
        }
        binding.tvStudentCode.text = profile.student?.studentCode
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.profile_student_code_format, it) }
            ?: getString(R.string.profile_student_code_empty)
        binding.tvPasswordStatus.text = if (profile.mustChangePassword == true) {
            getString(R.string.profile_password_must_change)
        } else {
            getString(R.string.profile_password_ok)
        }
        binding.tvCreatedAt.text = getString(
            R.string.profile_created_at_format,
            profile.createdAt?.toDisplayDateTime() ?: getString(R.string.common_not_updated)
        )

        if (pendingAvatarPart == null) {
            bindAvatar(profile.avatarUrl)
        }
        binding.tvProfileInitials.text = profile.fullName?.firstOrNull()?.toString()?.uppercase() ?: ""
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

    private fun prepareAvatar(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
        if (mimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            Snackbar.make(binding.root, R.string.settings_avatar_unsupported, Snackbar.LENGTH_LONG).show()
            return
        }

        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val file = File(requireContext().cacheDir, "avatar-${UUID.randomUUID()}.$extension")
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: error(getString(R.string.settings_avatar_read_failed))
        }.onFailure {
            Snackbar.make(binding.root, it.message ?: getString(R.string.settings_avatar_read_failed), Snackbar.LENGTH_LONG).show()
            return
        }

        if (file.length() > 2 * 1024 * 1024) {
            file.delete()
            Snackbar.make(binding.root, R.string.settings_avatar_too_large, Snackbar.LENGTH_LONG).show()
            return
        }

        val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
        pendingAvatarFile?.takeIf { it.exists() }?.delete()
        pendingAvatarFile = file
        pendingAvatarPart = MultipartBody.Part.createFormData("file", file.name, body)

        binding.ivProfileAvatar.visibility = View.VISIBLE
        binding.tvProfileInitials.visibility = View.GONE
        Glide.with(this)
            .load(file)
            .centerCrop()
            .into(binding.ivProfileAvatar)
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
}
