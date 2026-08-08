package com.gymmanager.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.ForgotPasswordResponse
import com.gymmanager.data.model.ResetPasswordResponse
import com.gymmanager.data.repository.AuthRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

/**
 * Backs the two-step Owner "forgot password" flow:
 *  1. requestCode(email)         → emails a 6-digit code
 *  2. submitReset(email, code, password) → verifies the code and sets the new password
 */
class ForgotPasswordViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _requestCodeResult = MutableLiveData<NetworkResult<ForgotPasswordResponse>>()
    val requestCodeResult: LiveData<NetworkResult<ForgotPasswordResponse>> = _requestCodeResult

    private val _resetResult = MutableLiveData<NetworkResult<ResetPasswordResponse>>()
    val resetResult: LiveData<NetworkResult<ResetPasswordResponse>> = _resetResult

    fun requestCode(email: String) {
        _requestCodeResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _requestCodeResult.value = repository.forgotPassword(email)
        }
    }

    fun submitReset(email: String, code: String, password: String) {
        _resetResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _resetResult.value = repository.resetPassword(email, code, password)
        }
    }
}
