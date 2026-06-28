package com.examhub.student.ui.login

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.examhub.student.R
import com.examhub.student.databinding.FragmentLoginBinding
import com.examhub.student.service.BackendUrlManager
import com.examhub.student.service.BackendUrlUpdateResult
import com.examhub.student.util.extension.add3DTouch
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    companion object {
        private const val TAG = "LoginFragment"
    }

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModel()
    private val backendUrlManager: BackendUrlManager by inject()
    private var googleSignInClient: GoogleSignInClient? = null

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    if (!idToken.isNullOrBlank()) {
                        Log.d(TAG, "Google ID Token obtained")
                        viewModel.loginWithGoogle(
                            idToken = idToken,
                            email = account.email,
                            fullName = account.displayName
                        )
                    } else {
                        viewModel.loginWithGoogle("")
                    }
                } catch (e: ApiException) {
                    Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
                    Snackbar.make(
                        binding.root,
                        googleErrorMessage(e.statusCode),
                        Snackbar.LENGTH_SHORT
                    ).show()
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
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initGoogleSignIn()
        setup3DTouch()
        setupClickListeners()
        observeViewModel()
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

    private fun setup3DTouch() {
        binding.btnLogin.add3DTouch()
        binding.btnRegister.add3DTouch(scaleTo = 0.97f)
        binding.btnGoogleSignIn.add3DTouch()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(email, password)
        }

        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot_password)
        }

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        binding.btnBackendSettings.setOnClickListener {
            showBackendUrlDialog()
        }
    }

    private fun showBackendUrlDialog() {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(backendUrlManager.currentBaseUrl())
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
                val messageRes = when (backendUrlManager.clearOverride()) {
                    BackendUrlUpdateResult.Changed -> R.string.settings_backend_url_reset_cleared
                    BackendUrlUpdateResult.Unchanged -> R.string.settings_backend_url_reset_done
                    BackendUrlUpdateResult.Invalid -> R.string.settings_backend_url_invalid
                }
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
            }
            .setPositiveButton(R.string.common_confirm) { _, _ ->
                val messageRes = when (backendUrlManager.saveOverride(input.text?.toString().orEmpty())) {
                    BackendUrlUpdateResult.Changed -> R.string.settings_backend_url_saved_cleared
                    BackendUrlUpdateResult.Unchanged -> R.string.settings_backend_url_unchanged
                    BackendUrlUpdateResult.Invalid -> R.string.settings_backend_url_invalid
                }
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun startGoogleSignIn() {
        val client = googleSignInClient
        if (client == null) {
            Snackbar.make(binding.root, R.string.login_google_not_configured, Snackbar.LENGTH_SHORT).show()
            return
        }

        binding.btnGoogleSignIn.isEnabled = false
        client.signOut()
            .addOnCompleteListener {
                if (_binding == null) return@addOnCompleteListener
                binding.btnGoogleSignIn.isEnabled = true
                googleSignInLauncher.launch(client.signInIntent)
            }
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.loginSuccess.collect {
                    findNavController().navigate(R.id.action_login_to_dashboard)
                }
            }

            launch {
                viewModel.errorMessage.collect { code ->
                    val message = when (code) {
                        "email_blank" -> getString(R.string.login_error_email_blank)
                        "password_blank" -> getString(R.string.login_error_password_blank)
                        "google_token_missing" -> getString(R.string.login_error_google_token)
                        "login_failed" -> getString(R.string.login_error_failed)
                        "google_login_failed" -> getString(R.string.login_error_google_failed)
                        "INVALID_GOOGLE_TOKEN" -> getString(R.string.auth_error_invalid_google_token)
                        "GOOGLE_EMAIL_MISMATCH" -> getString(R.string.auth_error_google_email_mismatch)
                        "GOOGLE_ACCOUNT_ALREADY_LINKED" -> getString(R.string.auth_error_google_already_linked)
                        "GOOGLE_ACCOUNT_MISMATCH" -> getString(R.string.auth_error_google_account_mismatch)
                        "ACCOUNT_INACTIVE" -> getString(R.string.auth_error_account_inactive)
                        "INVALID_CREDENTIALS" -> getString(R.string.auth_error_invalid_credentials)
                        else -> code
                    }
                    Snackbar.make(binding.root, message.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }

            launch {
                viewModel.googleRegistrationRequired.collect { prefill ->
                    val bundle = Bundle().apply {
                        putString("prefillEmail", prefill.email)
                        putString("prefillFullName", prefill.fullName)
                        putString("googleIdToken", prefill.googleIdToken)
                        putBoolean("startActivation", prefill.startActivation)
                    }
                    findNavController().navigate(R.id.action_login_to_register, bundle)
                }
            }

            launch {
                viewModel.activationRequired.collect { email ->
                    val bundle = Bundle().apply {
                        putString("prefillEmail", email)
                        putBoolean("startActivation", true)
                    }
                    findNavController().navigate(R.id.action_login_to_register, bundle)
                }
            }

            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    binding.btnLogin.isEnabled = !loading
                    binding.btnGoogleSignIn.isEnabled = !loading
                    binding.btnRegister.isEnabled = !loading
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
