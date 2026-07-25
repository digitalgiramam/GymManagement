package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.DashboardStats
import com.gymmanager.utils.NetworkResult

class DashboardRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getStats(): NetworkResult<DashboardStats> =
        safeApiCall { api.getDashboardStats() }
}
