package com.gymmanager.ui.staff

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Attendance
import com.gymmanager.data.repository.AttendanceRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class StaffAttendanceViewModel(private val repository: AttendanceRepository) : ViewModel() {

    private val _todayAttendance = MutableLiveData<NetworkResult<List<Attendance>>>()
    val todayAttendance: LiveData<NetworkResult<List<Attendance>>> = _todayAttendance

    private val _checkInResult = MutableLiveData<NetworkResult<Attendance>?>()
    val checkInResult: LiveData<NetworkResult<Attendance>?> = _checkInResult

    fun loadTodayAttendance() {
        _todayAttendance.value = NetworkResult.Loading
        viewModelScope.launch {
            _todayAttendance.value = repository.getTodayAttendance()
        }
    }

    fun checkIn(memberId: Int) {
        viewModelScope.launch {
            _checkInResult.value = repository.checkIn(memberId)
        }
    }
}
