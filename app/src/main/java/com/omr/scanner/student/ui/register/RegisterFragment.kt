package com.omr.scanner.student.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.omr.scanner.student.R
import com.omr.scanner.student.databinding.FragmentRegisterBinding
import com.omr.scanner.student.ui.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RegisterViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("prefillEmail").orEmpty().takeIf { it.isNotBlank() }?.let {
            binding.etEmail.setText(it)
        }
        arguments?.getString("prefillFullName").orEmpty().takeIf { it.isNotBlank() }?.let {
            binding.etFullName.setText(it)
        }

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnRegister.setOnClickListener {
            viewModel.register(
                binding.etFullName.text.toString(),
                binding.etEmail.text.toString(),
                binding.etPassword.text.toString(),
                binding.etConfirmPassword.text.toString()
            )
        }

        collectOnStarted {
            launch {
                viewModel.registerSuccess.collect {
                    findNavController().navigate(R.id.action_register_to_login)
                }
            }
            launch {
                viewModel.errorMessage.collect {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                }
            }
            launch {
                viewModel.isLoading.collect { loading ->
                    binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
