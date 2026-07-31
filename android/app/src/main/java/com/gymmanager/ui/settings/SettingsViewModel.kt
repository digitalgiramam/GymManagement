package com.gymmanager.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.TenantSettings
import com.gymmanager.data.model.UpdateSettingsRequest
import com.gymmanager.data.repository.SettingsRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _settings = MutableLiveData<TenantSettings?>()
    val settings: LiveData<TenantSettings?> = _settings

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _saveResult = MutableLiveData<NetworkResult<TenantSettings>?>()
    val saveResult: LiveData<NetworkResult<TenantSettings>?> = _saveResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init { loadSettings() }

    fun loadSettings() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.getSettings()) {
                is NetworkResult.Success -> { _settings.value = result.data; _error.value = null }
                is NetworkResult.Error   -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun saveSettings(request: UpdateSettingsRequest) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.updateSettings(request)
            _saveResult.value = result
            if (result is NetworkResult.Success) _settings.value = result.data
            _isLoading.value = false
        }
    }

    fun clearSaveResult() { _saveResult.value = null }
}
