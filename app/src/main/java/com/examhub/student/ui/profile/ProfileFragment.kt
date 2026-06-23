package com.examhub.student.ui.profile

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import android.text.Editable
import android.text.TextWatcher
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.util.Calendar
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
    private var bindingProfile = false
    private var originalProfileName = ""
    private var originalDateOfBirth = ""
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
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val statusCode = runCatching {
                    task.getResult(ApiException::class.java)
                    null
                }.exceptionOrNull()
                    ?.let { it as? ApiException }
                    ?.statusCode
                if (statusCode != com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    Snackbar.make(
                        binding.root,
                        statusCode?.let(::googleErrorMessage)
                            ?: getString(R.string.login_error_google_failed),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
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
        binding.etFullName.isEnabled = true
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

        binding.etDateOfBirth.setOnClickListener {
            if (!isSaving) showDatePickerDialog()
        }

        binding.btnSave.setOnClickListener {
            val fullName = binding.etFullName.text.toString().normalizedName()
            val dob = binding.etDateOfBirth.text.toString().normalizedDate().takeIf { it.isNotEmpty() }
            viewModel.saveProfile(
                fullName = fullName,
                dateOfBirth = dob,
                avatarFile = pendingAvatarPart,
                updateTextProfile = hasTextProfileChanges()
            )
        }

        val profileTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                checkProfileChanges()
            }
        }
        binding.etFullName.addTextChangedListener(profileTextWatcher)
        binding.etDateOfBirth.addTextChangedListener(profileTextWatcher)

        binding.btnSave.visibility = View.GONE
        updateSaveButton()

        collectOnStarted {
            launch {
                viewModel.isSaving.collect { saving ->
                    isSaving = saving
                    binding.progressBar.visibility = if (saving) View.VISIBLE else View.GONE
                    showUploadProgress(saving && viewModel.hasPendingAvatar.value)
                    updateSaveButton()
                }
            }
            launch {
                viewModel.saveSuccess.collect {
                    Snackbar.make(binding.root, R.string.profile_saved, Snackbar.LENGTH_SHORT).show()
                    viewModel.userProfile.value?.let { bindProfile(it, forceText = true) }
                }
            }
            launch {
                viewModel.avatarUploadSuccess.collect {
                    pendingAvatarFile?.takeIf { it.exists() }?.delete()
                    pendingAvatarPart = null
                    pendingAvatarFile = null
                    bindAvatar(viewModel.userProfile.value?.avatarUrl)
                    updateSaveButton()
                }
            }
            launch {
                viewModel.userProfile.collect { profile ->
                    hasProfile = profile != null
                    profile?.let { bindProfile(it) }
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
                viewModel.hasPendingAvatar.collect { pending ->
                    showUploadProgress(isSaving && pending)
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

    private fun bindProfile(profile: UserResponse, forceText: Boolean = false) {
        val rawName: String? = profile.fullName
        val rawRole: String? = profile.role
        val rawEmail: String? = profile.email
        val editableName = rawName.orEmpty().normalizedName()
        val dateOfBirth = profile.student?.dateOfBirth.normalizedDate()
        val currentName = binding.etFullName.text.toString().normalizedName()
        val currentDob = binding.etDateOfBirth.text.toString().normalizedDate()
        val hasUnsavedTextChanges = hasProfile &&
            (currentName != originalProfileName || currentDob != originalDateOfBirth)
        val preserveTextEdits = hasUnsavedTextChanges && !forceText
        val displayName = (if (preserveTextEdits) currentName else editableName)
            .toTitleCaseName()
            .ifBlank { getString(R.string.profile_name_placeholder) }
        bindingProfile = true
        binding.tvProfileDisplayName.text = displayName
        binding.tvProfileRole.text = rawRole.orEmpty()
            .ifBlank { getString(R.string.profile_role_student) }
            .uppercase(Locale.ROOT)
        binding.tvProfileStudentCodeTop.text = profile.student?.studentCode
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_student_code_top_empty)
        if (!preserveTextEdits) {
            originalProfileName = editableName
            originalDateOfBirth = dateOfBirth
            binding.etFullName.setText(editableName)
            binding.etDateOfBirth.setText(dateOfBirth)
        }
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
        bindingProfile = false
        updateSaveButton()
    }

    private fun updateSaveButton() {
        checkProfileChanges()
        binding.etFullName.isEnabled = !isSaving
        binding.etDateOfBirth.isEnabled = !isSaving
        binding.ivEditAvatar.isEnabled = !isSaving
        binding.ivEditAvatar.isClickable = !isSaving
        binding.btnGoogleLink.isEnabled = !isSaving && hasProfile
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

    private fun bindAvatarFile(file: File) {
        binding.ivProfileAvatar.visibility = View.VISIBLE
        binding.tvProfileInitials.visibility = View.GONE
        Glide.with(this)
            .load(file)
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
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = resources.getDimensionPixelSize(R.dimen.spacing_8)
            setPadding(0, padding, 0, padding)
        }
        var dialog: Dialog? = null

        container.addView(
            avatarSourceRow(R.drawable.ic_gallery, R.string.profile_avatar_source_gallery) {
                dialog?.dismiss()
                pickAvatarLauncher.launch("image/*")
            }
        )

        container.addView(
            avatarSourceRow(R.drawable.ic_camera, R.string.profile_avatar_source_camera) {
                dialog?.dismiss()
                takeAvatarLauncher.launch(null)
            }
        )

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_avatar_source_title)
            .setView(container)
            .create()

        dialog.show()
    }

    private var progressDialog: Dialog? = null

    private fun showUploadProgress(show: Boolean) {
        if (show) {
            if (progressDialog == null) {
                progressDialog = Dialog(requireContext()).apply {
                    setContentView(android.widget.ProgressBar(requireContext()))
                    window?.setBackgroundDrawableResource(android.R.color.transparent)
                    setCancelable(false)
                }
            }
            if (progressDialog?.isShowing == false) {
                progressDialog?.show()
            }
        } else {
            progressDialog?.dismiss()
            progressDialog = null
        }
    }

    private fun getRotatedBitmap(file: File): Bitmap? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return runCatching {
            val exif = android.media.ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            val rotationDegrees = when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (rotationDegrees != 0) {
                val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        }.getOrDefault(bitmap)
    }

    private fun prepareAvatar(uri: Uri) {
        val mimeType = requireContext().contentResolver.getType(uri) ?: "image/jpeg"
        if (mimeType !in setOf("image/jpeg", "image/png")) {
            Snackbar.make(binding.root, R.string.settings_avatar_unsupported, Snackbar.LENGTH_LONG).show()
            return
        }

        val extension = if (mimeType == "image/png") "png" else "jpg"
        val tempFile = File(requireContext().cacheDir, "temp-${UUID.randomUUID()}.$extension")
        runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error(getString(R.string.settings_avatar_read_failed))
        }.onFailure {
            Snackbar.make(
                binding.root,
                (it.message ?: getString(R.string.settings_avatar_read_failed)).replaceTechnicalLabels(),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val rotatedBitmap = getRotatedBitmap(tempFile)
        tempFile.delete()

        if (rotatedBitmap != null) {
            showCropDialog(rotatedBitmap)
        } else {
            Snackbar.make(binding.root, R.string.settings_avatar_read_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun prepareAvatar(bitmap: Bitmap) {
        showCropDialog(bitmap)
    }

    private fun showCropDialog(bitmap: Bitmap) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_crop_image, null)
        val cropImageView = dialogView.findViewById<CropImageView>(R.id.cropImageView)
        val zoomSeekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.zoomSeekBar)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<android.view.View>(R.id.btnSave)

        cropImageView.setImageBitmap(bitmap)

        zoomSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                cropImageView.setZoom(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val croppedBitmap = cropImageView.cropImage()
            if (croppedBitmap != null) {
                dialog.dismiss()
                uploadCroppedAvatar(croppedBitmap)
            } else {
                Snackbar.make(binding.root, R.string.settings_avatar_read_failed, Snackbar.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun uploadCroppedAvatar(bitmap: Bitmap) {
        val file = File(requireContext().cacheDir, "avatar-${UUID.randomUUID()}.jpg")
        runCatching {
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
            }
        }.onFailure {
            Snackbar.make(binding.root, R.string.settings_avatar_read_failed, Snackbar.LENGTH_LONG).show()
            return
        }

        if (file.length() > avatarMaxBytes) {
            file.delete()
            Snackbar.make(binding.root, R.string.settings_avatar_too_large, Snackbar.LENGTH_LONG).show()
            return
        }

        val body = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        pendingAvatarFile?.takeIf { it.exists() }?.delete()
        pendingAvatarFile = file
        val avatarPart = MultipartBody.Part.createFormData("file", file.name, body)
        pendingAvatarPart = avatarPart
        viewModel.setAvatarPending(true)
        bindAvatarFile(file)
        updateSaveButton()
    }

    private fun String.toDisplayDateTime(): String {
        return replace("T", " ")
            .removeSuffix("Z")
            .substringBefore(".")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressDialog?.dismiss()
        progressDialog = null
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

    private fun avatarSourceRow(iconRes: Int, textRes: Int, onClick: () -> Unit): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val vertical = resources.getDimensionPixelSize(R.dimen.spacing_12)
            val horizontal = resources.getDimensionPixelSize(R.dimen.spacing_24)
            setPadding(horizontal, vertical, horizontal, vertical)
            setOnClickListener { onClick() }

            addView(ImageView(requireContext()).apply {
                setImageResource(iconRes)
                setColorFilter(requireContext().getColor(R.color.primary))
                layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
            })

            addView(TextView(requireContext()).apply {
                text = getString(textRes)
                setTextColor(requireContext().getColor(R.color.text_primary))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.spacing_16)
                }
            })
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val currentText = binding.etDateOfBirth.text.toString().trim()
        if (currentText.isNotEmpty()) {
            val parts = currentText.split("-")
            if (parts.size == 3) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()?.minus(1)
                val day = parts[2].toIntOrNull()
                if (year != null && month != null && day != null) {
                    calendar.set(year, month, day)
                }
            }
        } else {
            calendar.set(2008, 0, 1)
        }

        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                binding.etDateOfBirth.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun checkProfileChanges() {
        if (bindingProfile) return
        val profile = viewModel.userProfile.value
        val hasChanges = hasTextProfileChanges() || pendingAvatarPart != null

        binding.btnSave.visibility = if (hasChanges) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isSaving &&
            hasChanges &&
            profile != null &&
            binding.etFullName.text.toString().normalizedName().isNotEmpty()
    }

    private fun hasTextProfileChanges(): Boolean {
        val profile = viewModel.userProfile.value ?: return false
        val originalName = originalProfileName.ifBlank { profile.fullName.orEmpty().normalizedName() }
        val originalDob = originalDateOfBirth.ifBlank { profile.student?.dateOfBirth.normalizedDate() }
        val currentName = binding.etFullName.text.toString().normalizedName()
        val currentDob = binding.etDateOfBirth.text.toString().normalizedDate()
        return currentName != originalName || currentDob != originalDob
    }

    private fun String?.normalizedName(): String =
        orEmpty().trim().replace(Regex("\\s+"), " ")

    private fun String?.normalizedDate(): String =
        orEmpty().substringBefore("T").trim()
}
