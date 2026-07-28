package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.Attendance
import com.gymmanager.data.model.CheckInRequest
import com.gymmanager.utils.NetworkResult

class AttendanceRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getTodayAttendance(): NetworkResult<List<Attendance>> =
        safeApiCall { api.getTodayAttendance() }

    suspend fun checkIn(memberId: Int): NetworkResult<Attendance> =
        safeApiCall { api.checkIn(CheckInRequest(memberId)) }

    suspend fun checkOut(attendanceId: Int): NetworkResult<Attendance> =
        safeApiCall { api.checkOut(attendanceId) }
}
