package com.examhub.student.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.navigation.fragment.findNavController
import com.examhub.student.R
import com.examhub.student.databinding.FragmentSplashBinding
import com.examhub.student.extension.collectOnStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SplashViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectOnStarted {
            launch {
                viewModel.statusText.collect { text ->
                    binding.tvStatus.text = text
                }
            }
            launch {
                viewModel.destination.collect { dest ->
                    if (dest == null) return@collect
                    when (dest) {
                        is SplashDestination.Login -> {
                            findNavController().navigate(R.id.action_splash_to_login)
                        }
                        is SplashDestination.Dashboard -> {
                            val bundle = Bundle().apply {
                                putString("mode", dest.mode)
                            }
                            findNavController().navigate(R.id.action_splash_to_dashboard, bundle)
                        }
                    }
                }
            }
        }

        viewModel.start(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
