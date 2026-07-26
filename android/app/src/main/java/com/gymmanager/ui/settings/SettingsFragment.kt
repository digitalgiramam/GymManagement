package com.gymmanager.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.gymmanager.data.model.UpdateSettingsRequest
import com.gymmanager.databinding.FragmentSettingsBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.showSnackbarError

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(requireContext().gymApp.settingsRepository) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveSettings.setOnClickListener { saveSettings() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnSaveSettings.isEnabled = !loading
        }

        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings == null) return@observe
            binding.etGymName.setText(settings.name)
            binding.etAddress.setText(settings.address ?: "")
            binding.etPhone.setText(settings.phone ?: "")
            binding.etCurrencySymbol.setText(settings.currencySymbol)
            binding.etCheckInWindow.setText(settings.checkInWindowMinutes.toString())
            binding.etTaxRate.setText(settings.taxRate.toString())
            // Keep stored currency symbol in sync so all pages reflect it immediately
            if (settings.currencySymbol.isNotBlank()) {
                requireContext().gymApp.tokenManager.saveCurrencySymbol(settings.currencySymbol)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) binding.root.showSnackbarError(msg)
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    // Persist updated currency symbol for immediate app-wide effect
                    result.data.currencySymbol.takeIf { it.isNotBlank() }
                        ?.let { requireContext().gymApp.tokenManager.saveCurrencySymbol(it) }
                    Snackbar.make(binding.root, "Settings saved!", Snackbar.LENGTH_SHORT).show()
                }
                is NetworkResult.Error ->
                    binding.root.showSnackbarError(result.message)
                else -> {}
            }
            if (result != null) viewModel.clearSaveResult()
        }
    }

    private fun saveSettings() {
        val name           = binding.etGymName.text?.toString()?.trim()
        val address        = binding.etAddress.text?.toString()?.trim()
        val phone          = binding.etPhone.text?.toString()?.trim()
        val currency       = binding.etCurrencySymbol.text?.toString()?.trim()
        val windowMinutes  = binding.etCheckInWindow.text?.toString()?.toIntOrNull()
        val taxRate        = binding.etTaxRate.text?.toString()?.toDoubleOrNull()

        if (name.isNullOrBlank()) {
            binding.root.showSnackbarError("Gym name is required")
            return
        }

        viewModel.saveSettings(
            UpdateSettingsRequest(
                name                 = name,
                address              = address?.ifBlank { null },
                phone                = phone?.ifBlank { null },
                currencySymbol       = currency?.ifBlank { null },
                checkInWindowMinutes = windowMinutes,
                taxRate              = taxRate,
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
