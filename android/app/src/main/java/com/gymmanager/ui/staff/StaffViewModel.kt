package com.gymmanager.ui.staff

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.CreateStaffRequest
import com.gymmanager.data.model.Staff
import com.gymmanager.data.repository.StaffRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class StaffViewModel(private val repository: StaffRepository) : ViewModel() {

    private val _staff = MutableLiveData<List<Staff>>(emptyList())
    val staff: LiveData<List<Staff>> = _staff

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _actionResult = MutableLiveData<NetworkResult<Any>?>()
    val actionResult: LiveData<NetworkResult<Any>?> = _actionResult

    init { loadStaff() }

    fun loadStaff() {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.getStaff()) {
                is NetworkResult.Success -> { _staff.value = result.data; _error.value = null }
                is NetworkResult.Error   -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun addStaff(request: CreateStaffRequest) {
        viewModelScope.launch {
            val result = repository.createStaff(request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadStaff()
        }
    }

    fun deleteStaff(id: Int) {
        viewModelScope.launch {
            val result = repository.deleteStaff(id)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadStaff()
        }
    }

    fun clearActionResult() { _actionResult.value = null }
}
