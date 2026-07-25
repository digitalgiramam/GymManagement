package com.gymmanager.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.LoginResponse
import com.gymmanager.data.repository.AuthRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<NetworkResult<LoginResponse>>()
    val loginResult: LiveData<NetworkResult<LoginResponse>> = _loginResult

    fun login(username: String, password: String) {
        if (username.isBlank()) {
            _loginResult.value = NetworkResult.Error("Username is required")
            return
        }
        if (password.isBlank()) {
            _loginResult.value = NetworkResult.Error("Password is required")
            return
        }

        _loginResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _loginResult.value = repository.login(username.trim(), password)
        }
    }
}
