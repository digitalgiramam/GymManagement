package com.gymmanager.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.AuthResponse
import com.gymmanager.data.repository.AuthRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _authResult = MutableLiveData<NetworkResult<AuthResponse>>()
    val authResult: LiveData<NetworkResult<AuthResponse>> = _authResult

    fun login(email: String, password: String) {
        _authResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _authResult.value = repository.login(email, password)
        }
    }

    fun register(email: String, password: String, name: String) {
        _authResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _authResult.value = repository.register(email, password, name)
        }
    }

    fun staffLogin(email: String, password: String) {
        _authResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _authResult.value = repository.staffLogin(email, password)
        }
    }

    fun memberLogin(email: String, password: String) {
        _authResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _authResult.value = repository.memberLogin(email, password)
        }
    }
}
