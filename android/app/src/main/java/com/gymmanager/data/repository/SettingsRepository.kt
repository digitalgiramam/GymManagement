package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class SettingsRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getSettings(): NetworkResult<TenantSettings> =
        safeApiCall { api.getSettings() }

    suspend fun updateSettings(request: UpdateSettingsRequest): NetworkResult<TenantSettings> =
        safeApiCall { api.updateSettings(request) }
}
