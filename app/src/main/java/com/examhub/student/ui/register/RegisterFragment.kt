package com.examhub.student.ui.register

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.KeyEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.examhub.student.R
import com.examhub.student.databinding.FragmentRegisterBinding
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.Calendar
import java.util.Locale

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RegisterViewModel by viewModel()
    private val otpEditTexts = mutableListOf<EditText>()
    private var googleIdToken: String = ""
    private var isLoading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        googleIdToken = arguments?.getString("googleIdToken").orEmpty()
        setupPrefill()
        viewModel.initializePrefill(
            email = arguments?.getString("prefillEmail").orEmpty(),
            startActivation = arguments?.getBoolean("startActivation", false) == true
        )
        setupGoogleRegistrationMode()
        setupOtpFields()
        setupClickListeners()
        setupFormValidation()
        observeViewModel()
        updateRegisterButtonState()
    }

    private fun setupPrefill() {
        arguments?.getString("prefillEmail").orEmpty().takeIf { it.isNotBlank() }?.let {
            binding.etEmail.setText(it)
        }
        arguments?.getString("prefillFullName").orEmpty().takeIf { it.isNotBlank() }?.let {
            binding.etFullName.setText(it)
        }
    }

    private fun setupGoogleRegistrationMode() {
        val isGoogleRegistration = googleIdToken.isNotBlank()
        binding.tilPassword.isVisible = !isGoogleRegistration
        binding.tilConfirmPassword.isVisible = !isGoogleRegistration
        binding.etEmail.isEnabled = !isGoogleRegistration || binding.etEmail.text.isNullOrBlank()
    }

    private fun setupOtpFields() {
        repeat(6) { index ->
            val editText = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(1))
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48)).apply {
                    marginEnd = dpToPx(8)
                }
                setBackgroundResource(R.drawable.bg_input_field)
                maxLines = 1
            }
            editText.addTextChangedListener(object : TextWatcher {
                private var editing = false

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (editing) return
                    val digits = s?.filter(Char::isDigit)?.toString().orEmpty()
                    editing = true
                    editText.setText(digits.take(1))
                    editText.setSelection(editText.text?.length ?: 0)
                    editing = false
                    if (digits.isNotEmpty() && index < 5) {
                        otpEditTexts[index + 1].requestFocus()
                    }
                }
            })
            editText.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    editText.text.isNullOrEmpty() &&
                    index > 0
                ) {
                    otpEditTexts[index - 1].requestFocus()
                    otpEditTexts[index - 1].text?.clear()
                    true
                } else {
                    false
                }
            }
            binding.otpContainer.addView(editText)
            otpEditTexts.add(editText)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.etDateOfBirth.setOnClickListener {
            showDatePickerDialog()
        }

        binding.btnRegister.setOnClickListener {
            if (!validateForm(showErrors = true)) return@setOnClickListener
            viewModel.register(
                fullName = binding.etFullName.text.toString(),
                email = binding.etEmail.text.toString(),
                password = binding.etPassword.text.toString(),
                confirmPassword = binding.etConfirmPassword.text.toString(),
                studentCode = binding.etStudentCode.text.toString(),
                googleIdToken = googleIdToken,
                dateOfBirth = binding.etDateOfBirth.text.toString().takeIf { it.isNotBlank() }
            )
        }

        binding.btnVerifyOtp.setOnClickListener {
            val otp = otpEditTexts.joinToString("") { it.text.toString() }
            viewModel.verifyOtp(otp)
        }

        binding.btnResendOtp.setOnClickListener {
            viewModel.resendOtp()
        }

        binding.btnLoginLink.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
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

    private fun setupFormValidation() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                clearFieldErrors()
                updateRegisterButtonState()
            }
        }
        listOf(
            binding.etFullName,
            binding.etEmail,
            binding.etStudentCode,
            binding.etPassword,
            binding.etConfirmPassword
        ).forEach { editText ->
            editText.addTextChangedListener(watcher)
        }
    }

    private fun validateForm(showErrors: Boolean): Boolean {
        val isGoogleRegistration = googleIdToken.isNotBlank()
        val fullName = binding.etFullName.text?.toString().orEmpty().trim()
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val studentCode = binding.etStudentCode.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()

        var valid = true
        if (fullName.isBlank()) {
            valid = false
            if (showErrors) binding.tilFullName.error = getString(R.string.register_validation_name_required)
        }
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            valid = false
            if (showErrors) binding.tilEmail.error = getString(R.string.login_validation_email_invalid)
        }
        if (studentCode.isNotBlank() && !studentCode.all(Char::isDigit)) {
            valid = false
            if (showErrors) binding.tilStudentCode.error = getString(R.string.register_validation_student_code_digits)
        }
        if (!isGoogleRegistration && password.length < 6) {
            valid = false
            if (showErrors) binding.tilPassword.error = getString(R.string.register_validation_password_short)
        }
        if (!isGoogleRegistration && confirmPassword != password) {
            valid = false
            if (showErrors) binding.tilConfirmPassword.error = getString(R.string.register_validation_password_mismatch)
        }
        return valid
    }

    private fun clearFieldErrors() {
        binding.tilFullName.error = null
        binding.tilEmail.error = null
        binding.tilStudentCode.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun updateRegisterButtonState() {
        binding.btnRegister.isEnabled = !isLoading && validateForm(showErrors = false)
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.currentStep.collect { step ->
                    showOtpStep(step == 2)
                }
            }
            launch {
                viewModel.registerSuccess.collect { destination ->
                    when (destination) {
                        RegisterDestination.Login -> findNavController().navigate(R.id.action_register_to_login)
                        RegisterDestination.Dashboard -> findNavController().navigate(R.id.action_register_to_dashboard)
                    }
                }
            }
            launch {
                viewModel.message.collect {
                    Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    isLoading = loading
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    binding.btnRegister.text = getString(if (loading) R.string.register_loading else R.string.register_button)
                    updateRegisterButtonState()
                    binding.btnVerifyOtp.isEnabled = !loading
                    binding.btnResendOtp.isEnabled = !loading
                }
            }
        }
    }

    private fun showOtpStep(showOtp: Boolean) {
        binding.registerFormContent.isVisible = !showOtp
        binding.registerOtpContent.isVisible = showOtp
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        otpEditTexts.clear()
    }
}
