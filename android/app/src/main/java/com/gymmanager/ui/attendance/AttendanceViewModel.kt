package com.gymmanager.ui.attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Attendance
import com.gymmanager.data.model.Member
import com.gymmanager.data.repository.AttendanceRepository
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class AttendanceViewModel(
    private val attendanceRepo: AttendanceRepository,
    private val memberRepo: MemberRepository,
) : ViewModel() {

    private val _attendance = MutableLiveData<NetworkResult<List<Attendance>>>()
    val attendance: LiveData<NetworkResult<List<Attendance>>> = _attendance

    private val _members = MutableLiveData<List<Member>>()
    val members: LiveData<List<Member>> = _members

    private val _checkInResult = MutableLiveData<NetworkResult<Attendance>>()
    val checkInResult: LiveData<NetworkResult<Attendance>> = _checkInResult

    init {
        loadAttendance()
        loadMembers()
    }

    fun loadAttendance() {
        _attendance.value = NetworkResult.Loading
        viewModelScope.launch {
            _attendance.value = attendanceRepo.getTodayAttendance()
        }
    }

    fun loadMembers() {
        viewModelScope.launch {
            val result = memberRepo.getMembers()
            if (result is NetworkResult.Success) _members.value = result.data
        }
    }

    fun checkIn(memberId: Int) {
        _checkInResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = attendanceRepo.checkIn(memberId)
            _checkInResult.value = result
            if (result is NetworkResult.Success) loadAttendance()
        }
    }
}
