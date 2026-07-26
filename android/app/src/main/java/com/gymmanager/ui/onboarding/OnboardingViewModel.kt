package com.gymmanager.ui.onboarding

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.CreateGymRequest
import com.gymmanager.data.model.CreateGymResponse
import com.gymmanager.data.repository.AuthRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class OnboardingViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _createGymResult = MutableLiveData<NetworkResult<CreateGymResponse>>()
    val createGymResult: LiveData<NetworkResult<CreateGymResponse>> = _createGymResult

    fun createGym(gymName: String, address: String?, phone: String?, currencySymbol: String) {
        if (gymName.isBlank()) {
            _createGymResult.value = NetworkResult.Error("Gym name is required")
            return
        }
        _createGymResult.value = NetworkResult.Loading
        viewModelScope.launch {
            _createGymResult.value = repository.createGym(
                CreateGymRequest(
                    gymName        = gymName.trim(),
                    address        = address?.trim()?.ifBlank { null },
                    phone          = phone?.trim()?.ifBlank { null },
                    currencySymbol = currencySymbol.ifBlank { "$" },
                )
            )
        }
    }
}
