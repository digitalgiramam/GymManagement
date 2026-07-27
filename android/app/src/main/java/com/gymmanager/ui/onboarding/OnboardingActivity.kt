package com.gymmanager.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
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

    // Same currency options as SettingsFragment
    private data class CurrencyOption(val label: String, val symbol: String)
    private val currencyOptions = listOf(
        CurrencyOption("₹  Rs  — Indian Rupee",     "₹"),
        CurrencyOption("AED — UAE Dirham",           "AED"),
        CurrencyOption("\$  USD — US Dollar",        "$"),
        CurrencyOption("S\$ SGD — Singapore Dollar", "S$"),
    )
    private var selectedCurrencySymbol = "₹"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Pre-fill owner name from stored profile
        val userName = gymApp.tokenManager.getUserName()
        if (!userName.isNullOrBlank()) {
            binding.tvWelcomeUser.text = "Welcome, $userName!"
        }

        setupCurrencyDropdown()
        setupObservers()
        binding.btnCreateGym.setOnClickListener { submitForm() }
    }

    private fun setupCurrencyDropdown() {
        val labels  = currencyOptions.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        binding.actvCurrency.setAdapter(adapter)
        // Default selection
        binding.actvCurrency.setText(currencyOptions[0].label, false)
        binding.actvCurrency.setOnItemClickListener { _, _, position, _ ->
            selectedCurrencySymbol = currencyOptions[position].symbol
        }
    }

    private fun submitForm() {
        val gymName = binding.etGymName.text?.toString() ?: ""
        val address = binding.etAddress.text?.toString()
        val phone   = binding.etPhone.text?.toString()

        viewModel.createGym(gymName, address, phone, selectedCurrencySymbol)
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
