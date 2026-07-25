package com.gymmanager.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.DashboardStats
import com.gymmanager.data.repository.DashboardRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: DashboardRepository) : ViewModel() {

    private val _stats = MutableLiveData<NetworkResult<DashboardStats>>()
    val stats: LiveData<NetworkResult<DashboardStats>> = _stats

    init { loadStats() }

    fun loadStats() {
        _stats.value = NetworkResult.Loading
        viewModelScope.launch {
            _stats.value = repository.getStats()
        }
    }
}
