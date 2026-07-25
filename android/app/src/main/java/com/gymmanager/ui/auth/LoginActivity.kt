package com.gymmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.ActivityLoginBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.main.MainActivity
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(gymApp.authRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Skip login if token is already stored
        if (gymApp.tokenManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.show()
                    binding.btnLogin.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
                    navigateToMain()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.btnLogin.isEnabled = true
                    binding.root.showSnackbarError(result.message)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text?.toString() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            viewModel.login(username, password)
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
