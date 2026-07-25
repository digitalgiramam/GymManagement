package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.local.TokenManager
import com.gymmanager.data.model.LoginRequest
import com.gymmanager.data.model.LoginResponse
import com.gymmanager.utils.NetworkResult

class AuthRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager,
) : BaseRepository() {

    suspend fun login(username: String, password: String): NetworkResult<LoginResponse> {
        val result = safeApiCall { api.login(LoginRequest(username, password)) }
        if (result is NetworkResult.Success) {
            tokenManager.saveToken(result.data.token)
        }
        return result
    }

    fun logout() = tokenManager.clearToken()

    fun isLoggedIn() = tokenManager.isLoggedIn()
}
