package com.gymmanager.ui.member

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Attendance
import com.gymmanager.data.model.MemberProfile
import com.gymmanager.data.repository.MemberPortalRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class MemberPortalViewModel(private val repository: MemberPortalRepository) : ViewModel() {

    private val _profile    = MutableLiveData<NetworkResult<MemberProfile>>()
    val profile: LiveData<NetworkResult<MemberProfile>> = _profile

    private val _attendance = MutableLiveData<NetworkResult<List<Attendance>>>()
    val attendance: LiveData<NetworkResult<List<Attendance>>> = _attendance

    fun loadAll() {
        _profile.value = NetworkResult.Loading
        viewModelScope.launch {
            _profile.value    = repository.getProfile()
            _attendance.value = repository.getAttendance()
        }
    }
}
