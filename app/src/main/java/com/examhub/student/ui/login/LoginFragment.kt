package com.examhub.student.ui.login

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.BuildConfig
import com.examhub.student.R
import com.examhub.student.databinding.FragmentLoginBinding
import com.examhub.student.ui.add3DTouch
import com.examhub.student.ui.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    companion object {
        private const val TAG = "LoginFragment"
    }

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LoginViewModel by viewModel()
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
                    Snackbar.make(binding.root, "Đăng nhập Google thất bại (${e.statusCode})", Snackbar.LENGTH_SHORT).show()
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
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
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
            googleSignInClient?.signInIntent?.let { intent ->
                googleSignInLauncher.launch(intent)
            } ?: Snackbar.make(binding.root, "Google Sign-In chưa được cấu hình", Snackbar.LENGTH_SHORT).show()
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
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
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
