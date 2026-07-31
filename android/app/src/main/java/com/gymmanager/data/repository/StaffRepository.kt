package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class StaffRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getStaff(): NetworkResult<List<Staff>> =
        safeApiCall { api.getStaff() }

    suspend fun createStaff(request: CreateStaffRequest): NetworkResult<Staff> =
        safeApiCall { api.createStaff(request) }

    suspend fun updateStaff(id: Int, request: UpdateStaffRequest): NetworkResult<Staff> =
        safeApiCall { api.updateStaff(id, request) }

    suspend fun deleteStaff(id: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteStaff(id) }
}
