package com.gymmanager.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.ActivityForgotPasswordBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError

/**
 * Owner-only "forgot password" flow: request a 6-digit code by email (step 1),
 * then enter that code + a new password to complete the reset (step 2).
 * No deep-linking is configured in the app, so the code is typed in directly
 * rather than delivered as a clickable link.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    private val viewModel: ForgotPasswordViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ForgotPasswordViewModel(gymApp.authRepository) as T
            }
        })[ForgotPasswordViewModel::class.java]
    }

    /** Locks the email used for step 2 to whatever step 1 actually sent the code to. */
    private var emailForReset: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnSendCode.setOnClickListener { handleSendCode() }
        binding.btnResetPassword.setOnClickListener { handleResetPassword() }
        binding.tvResendCode.setOnClickListener { handleSendCode(isResend = true) }
        binding.tvBackToLogin.setOnClickListener { finish() }
    }

    private fun handleSendCode(isResend: Boolean = false) {
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        if (email.isBlank()) {
            binding.root.showSnackbarError("Please enter your email.")
            return
        }
        emailForReset = email
        viewModel.requestCode(email)
    }

    private fun handleResetPassword() {
        val code            = binding.etCode.text?.toString()?.trim() ?: ""
        val password        = binding.etNewPassword.text?.toString() ?: ""
        val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""

        if (code.length != 6) {
            binding.root.showSnackbarError("Please enter the 6-digit code from your email.")
            return
        }
        if (password.length < 6) {
            binding.root.showSnackbarError("Password must be at least 6 characters.")
            return
        }
        if (password != confirmPassword) {
            binding.root.showSnackbarError("Passwords do not match.")
            return
        }

        viewModel.submitReset(emailForReset, code, password)
    }

    private fun setupObservers() {
        viewModel.requestCodeResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.show()
                    binding.btnSendCode.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    binding.btnSendCode.isEnabled = true
                    binding.groupStep2.show()
                    binding.root.showSnackbar(result.data.message)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.btnSendCode.isEnabled = true
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.resetResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.show()
                    binding.btnResetPassword.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    binding.btnResetPassword.isEnabled = true
                    binding.root.showSnackbar("Password reset. Please sign in with your new password.")
                    finish()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.btnResetPassword.isEnabled = true
                    binding.root.showSnackbarError(result.message)
                }
            }
        }
    }
}
