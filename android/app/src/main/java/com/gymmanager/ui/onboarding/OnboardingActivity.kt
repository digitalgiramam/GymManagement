package com.gymmanager.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.ActivityOnboardingBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.main.MainActivity
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val viewModel: OnboardingViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(gymApp.authRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill owner name from stored Google profile
        val userName = gymApp.tokenManager.getUserName()
        if (!userName.isNullOrBlank()) {
            binding.tvWelcomeUser.text = "Welcome, $userName!"
        }

        setupObservers()
        binding.btnCreateGym.setOnClickListener { submitForm() }
    }

    private fun submitForm() {
        val gymName        = binding.etGymName.text?.toString() ?: ""
        val address        = binding.etAddress.text?.toString()
        val phone          = binding.etPhone.text?.toString()
        val currencySymbol = binding.etCurrencySymbol.text?.toString()?.trim() ?: "$"

        viewModel.createGym(gymName, address, phone, currencySymbol)
    }

    private fun setupObservers() {
        viewModel.createGymResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.show()
                    binding.btnCreateGym.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    navigateToMain()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.btnCreateGym.isEnabled = true
                    binding.root.showSnackbarError(result.message)
                }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
