package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class MemberPortalRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getProfile(): NetworkResult<MemberProfile> =
        safeApiCall { api.getMemberProfile() }

    suspend fun getAttendance(): NetworkResult<List<Attendance>> =
        safeApiCall { api.getMemberAttendance() }
}
