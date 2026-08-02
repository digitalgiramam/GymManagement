package com.gymmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.databinding.ActivityLoginBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.main.MainActivity
import com.gymmanager.ui.member.MemberPortalActivity
import com.gymmanager.ui.onboarding.OnboardingActivity
import com.gymmanager.ui.staff.StaffDashboardActivity
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError

/** Login type selected by the chip group */
enum class LoginRole { OWNER, STAFF, MEMBER }

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: LoginViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(gymApp.authRepository) as T
            }
        })[LoginViewModel::class.java]
    }

    private var isRegisterMode = false
    private var selectedRole = LoginRole.OWNER

    private val roleOptions = listOf(
        "Owner"  to LoginRole.OWNER,
        "Staff"  to LoginRole.STAFF,
        "Member" to LoginRole.MEMBER,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Skip login if already authenticated
        if (gymApp.tokenManager.isLoggedIn()) {
            navigateNext()
            return
        }

        setupRoleDropdown()
        setupListeners()
        setupObservers()
    }

    private fun setupRoleDropdown() {
        binding.spinnerRole.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, roleOptions.map { it.first },
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerRole.setSelection(roleOptions.indexOfFirst { it.second == selectedRole })

        binding.spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRole = roleOptions[position].second
                // Staff/Member can only sign in, not register
                if (selectedRole != LoginRole.OWNER && isRegisterMode) {
                    isRegisterMode = false
                }
                updateModeUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnSubmit.setOnClickListener { handleSubmit() }
        binding.tvToggleMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateModeUI()
        }
    }

    private fun updateModeUI() {
        val isOwner  = selectedRole == LoginRole.OWNER
        val isMember = selectedRole == LoginRole.MEMBER

        // Register mode only available for owners
        binding.tvToggleMode.visibility = if (isOwner) View.VISIBLE else View.GONE

        // For members: allow phone number entry — switch to plain text input type
        if (isMember) {
            binding.tilEmail.hint = "Email or Phone"
            binding.etEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            binding.tilEmail.hint = "Email"
            binding.etEmail.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        if (isRegisterMode && isOwner) {
            binding.tilName.visibility = View.VISIBLE
            binding.btnSubmit.text     = "Create Account"
            binding.tvToggleMode.text  = "Already have an account? Sign in"
            binding.tvTitle.text       = "Create Account"
        } else {
            binding.tilName.visibility = View.GONE
            val label = when (selectedRole) {
                LoginRole.OWNER  -> "Sign In"
                LoginRole.STAFF  -> "Staff Sign In"
                LoginRole.MEMBER -> "Member Sign In"
            }
            binding.btnSubmit.text    = label
            binding.tvToggleMode.text = "Don't have an account? Register"
            binding.tvTitle.text      = when (selectedRole) {
                LoginRole.OWNER  -> "Sign In"
                LoginRole.STAFF  -> "Staff Portal"
                LoginRole.MEMBER -> "Member Portal"
            }
        }
    }

    private fun handleSubmit() {
        val email    = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""
        val name     = binding.etName.text?.toString()?.trim() ?: ""

        if (email.isBlank() || password.isBlank()) {
            binding.root.showSnackbarError("Please enter email and password.")
            return
        }
        if (isRegisterMode && name.isBlank()) {
            binding.root.showSnackbarError("Please enter your name.")
            return
        }

        when {
            isRegisterMode          -> viewModel.register(email, password, name)
            selectedRole == LoginRole.STAFF  -> viewModel.staffLogin(email, password)
            selectedRole == LoginRole.MEMBER -> viewModel.memberLogin(email, password)
            else                    -> viewModel.login(email, password)
        }
    }

    private fun setupObservers() {
        viewModel.authResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Loading -> {
                    binding.progressBar.show()
                    binding.btnSubmit.isEnabled    = false
                    binding.tvToggleMode.isEnabled = false
                }
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    binding.btnSubmit.isEnabled    = true
                    binding.tvToggleMode.isEnabled = true
                    navigateNext()
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.btnSubmit.isEnabled    = true
                    binding.tvToggleMode.isEnabled = true
                    binding.root.showSnackbarError(result.message)
                }
            }
        }
    }

    private fun navigateNext() {
        val destination = when {
            gymApp.tokenManager.isStaff()  -> Intent(this, StaffDashboardActivity::class.java)
            gymApp.tokenManager.isMember() -> Intent(this, MemberPortalActivity::class.java)
            else -> {
                // OWNER
                if (gymApp.tokenManager.hasCompletedOnboarding()) {
                    Intent(this, MainActivity::class.java)
                } else {
                    Intent(this, OnboardingActivity::class.java)
                }
            }
        }
        startActivity(destination)
        finish()
    }
}
