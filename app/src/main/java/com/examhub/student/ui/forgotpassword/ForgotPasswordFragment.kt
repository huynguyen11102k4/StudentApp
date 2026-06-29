package com.examhub.student.ui.forgotpassword

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.examhub.student.R
import com.examhub.student.databinding.FragmentForgotPasswordBinding
import com.examhub.student.util.extension.collectOnStarted
import com.examhub.student.util.extension.replaceTechnicalLabels
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ForgotPasswordViewModel by viewModel()
    private val otpEditTexts = mutableListOf<EditText>()
    private var editingOtp = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupOTPFields()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupOTPFields() {
        repeat(6) { index ->
            val editText = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(48),
                    dpToPx(48)
                ).apply {
                    marginEnd = dpToPx(8)
                }
                setBackgroundResource(R.drawable.bg_input_field)
                setTextColor(requireContext().getColor(R.color.text_primary))
                setHintTextColor(requireContext().getColor(R.color.text_hint))
                maxLines = 1
            }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (editingOtp) return
                    val digits = s?.filter(Char::isDigit)?.toString().orEmpty()
                    editingOtp = true
                    fillOtpDigits(index, digits)
                    editingOtp = false
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

    private fun fillOtpDigits(startIndex: Int, digits: String) {
        if (digits.isEmpty()) return
        val chars = digits.take(otpEditTexts.size - startIndex)
        chars.forEachIndexed { offset, char ->
            val field = otpEditTexts[startIndex + offset]
            field.setText(char.toString())
            field.setSelection(field.text?.length ?: 0)
        }
        val nextIndex = (startIndex + chars.length).coerceAtMost(otpEditTexts.lastIndex)
        otpEditTexts[nextIndex].requestFocus()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            viewModel.resetFlow()
            findNavController().navigateUp()
        }

        binding.btnSendOTP.setOnClickListener {
            viewModel.sendOTP(binding.etEmail.text.toString())
        }

        binding.btnVerifyOTP.setOnClickListener {
            val otp = otpEditTexts.map { it.text.toString() }.joinToString("")
            viewModel.verifyOTP(otp)
        }

        binding.btnResetPassword.setOnClickListener {
            viewModel.resetPassword(
                binding.etNewPassword.text.toString(),
                binding.etConfirmPassword.text.toString()
            )
        }
    }

    private fun observeViewModel() {
        collectOnStarted {
            launch {
                viewModel.currentStep.collect { step ->
                    binding.step1Content.isVisible = step == 1
                    binding.step2Content.isVisible = step == 2
                    binding.step3Content.isVisible = step == 3

                    val primaryColor = requireContext().getColor(R.color.primary)
                    val defaultBg = requireContext().getColor(R.color.surface_container_highest)

                    val textOnPrimary = requireContext().getColor(R.color.on_primary)
                    val textSecondary = requireContext().getColor(R.color.text_secondary)

                    binding.step1Indicator.backgroundTintList = ColorStateList.valueOf(if (step >= 1) primaryColor else defaultBg)
                    binding.step2Indicator.backgroundTintList = ColorStateList.valueOf(if (step >= 2) primaryColor else defaultBg)
                    binding.step3Indicator.backgroundTintList = ColorStateList.valueOf(if (step >= 3) primaryColor else defaultBg)

                    binding.step1Indicator.setTextColor(if (step >= 1) textOnPrimary else textSecondary)
                    binding.step2Indicator.setTextColor(if (step >= 2) textOnPrimary else textSecondary)
                    binding.step3Indicator.setTextColor(if (step >= 3) textOnPrimary else textSecondary)
                }
            }
            launch {
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it.replaceTechnicalLabels(), Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.isVisible = loading
                    binding.btnSendOTP.isEnabled = !loading
                    binding.btnVerifyOTP.isEnabled = !loading
                    binding.btnResetPassword.isEnabled = !loading
                    binding.btnBack.isEnabled = !loading
                    binding.etEmail.isEnabled = !loading
                    binding.etNewPassword.isEnabled = !loading
                    binding.etConfirmPassword.isEnabled = !loading
                    otpEditTexts.forEach { it.isEnabled = !loading }
                }
            }
            launch {
                viewModel.passwordResetSuccess.collect {
                    viewModel.resetFlow()
                    findNavController().navigate(R.id.action_forgot_password_to_login)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.resetFlow()
        _binding = null
        otpEditTexts.clear()
    }
}
