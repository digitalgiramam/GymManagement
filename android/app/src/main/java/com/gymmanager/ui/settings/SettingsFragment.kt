package com.gymmanager.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.gymmanager.R
import com.gymmanager.data.model.UpdateSettingsRequest
import com.gymmanager.databinding.FragmentSettingsBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.showSnackbarError
import java.io.ByteArrayOutputStream

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

    // ── Currency options ───────────────────────────────────────────────────
    private data class CurrencyOption(val label: String, val symbol: String)

    private val currencyOptions = listOf(
        CurrencyOption("₹  Rs  — Indian Rupee",     "₹"),
        CurrencyOption("AED — UAE Dirham",           "AED"),
        CurrencyOption("\$  USD — US Dollar",        "$"),
        CurrencyOption("S\$ SGD — Singapore Dollar", "S$"),
    )

    private var selectedCurrencySymbol: String = "₹"

    // ── Logo state ─────────────────────────────────────────────────────────
    private var pendingLogoBase64: String? = null   // set when user picks a new image

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> handleLogoSelected(uri) }
            }
        }

    // ──────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCurrencyDropdown()
        binding.btnChangeLogo.setOnClickListener { openImagePicker() }
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        observeViewModel()
    }

    // ── Currency dropdown ──────────────────────────────────────────────────
    private fun setupCurrencyDropdown() {
        val labels  = currencyOptions.map { it.label }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        binding.actvCurrency.setAdapter(adapter)
        binding.actvCurrency.setOnItemClickListener { _, _, position, _ ->
            selectedCurrencySymbol = currencyOptions[position].symbol
        }
    }

    private fun setCurrencyBySymbol(symbol: String) {
        selectedCurrencySymbol = symbol
        val option = currencyOptions.firstOrNull { it.symbol == symbol }
            ?: currencyOptions[0]
        binding.actvCurrency.setText(option.label, false)
        selectedCurrencySymbol = option.symbol
    }

    // ── Logo picker ────────────────────────────────────────────────────────
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        pickImageLauncher.launch(intent)
    }

    private fun handleLogoSelected(uri: Uri) {
        try {
            val stream  = requireContext().contentResolver.openInputStream(uri) ?: return
            val bitmap  = BitmapFactory.decodeStream(stream)
            stream.close()

            // Scale down to max 300×300 to keep DB size reasonable
            val scaled  = scaleBitmap(bitmap, 300)

            // Show preview
            binding.ivLogo.setImageBitmap(scaled)

            // Encode to base64
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val bytes = out.toByteArray()
            pendingLogoBase64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            binding.root.showSnackbarError("Failed to load image: ${e.message}")
        }
    }

    private fun scaleBitmap(src: Bitmap, maxPx: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxPx && h <= maxPx) return src
        val scale = maxPx.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun displayLogoFromBase64(base64: String?) {
        if (base64.isNullOrBlank()) return
        try {
            val pure  = base64.removePrefix("data:image/jpeg;base64,")
                              .removePrefix("data:image/png;base64,")
            val bytes = Base64.decode(pure, Base64.NO_WRAP)
            val bmp   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            binding.ivLogo.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    // ── Observe ────────────────────────────────────────────────────────────
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.btnSaveSettings.isEnabled = !loading
        }

        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings == null) return@observe
            binding.etGymName.setText(settings.name)
            binding.etContactPerson.setText(settings.contactPerson ?: "")
            binding.etAddress.setText(settings.address ?: "")
            binding.etPhone.setText(settings.phone ?: "")
            binding.etCheckInWindow.setText(settings.checkInWindowMinutes.toString())
            binding.etTaxRate.setText(settings.taxRate.toString())
            setCurrencyBySymbol(settings.currencySymbol)
            displayLogoFromBase64(settings.logoBase64)
            // Sync currency into token store immediately
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

    // ── Save ───────────────────────────────────────────────────────────────
    private fun saveSettings() {
        val name    = binding.etGymName.text?.toString()?.trim()
        if (name.isNullOrBlank()) {
            binding.root.showSnackbarError("Gym name is required")
            return
        }

        viewModel.saveSettings(
            UpdateSettingsRequest(
                name                 = name,
                contactPerson        = binding.etContactPerson.text?.toString()?.trim()?.ifBlank { null },
                address              = binding.etAddress.text?.toString()?.trim()?.ifBlank { null },
                phone                = binding.etPhone.text?.toString()?.trim()?.ifBlank { null },
                currencySymbol       = selectedCurrencySymbol,
                checkInWindowMinutes = binding.etCheckInWindow.text?.toString()?.toIntOrNull(),
                taxRate              = binding.etTaxRate.text?.toString()?.toDoubleOrNull(),
                logoBase64           = pendingLogoBase64,   // null = don't change, non-null = update
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
