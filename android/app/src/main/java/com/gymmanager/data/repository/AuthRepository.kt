package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.local.TokenManager
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class AuthRepository(
    private val api: ApiService,
    private val tokenManager: TokenManager,
) : BaseRepository() {

    suspend fun login(email: String, password: String): NetworkResult<AuthResponse> {
        val result = safeApiCall { api.login(LoginRequest(email, password)) }
        if (result is NetworkResult.Success) saveSession(result.data)
        return result
    }

    suspend fun register(email: String, password: String, name: String): NetworkResult<AuthResponse> {
        val result = safeApiCall { api.register(RegisterRequest(email, password, name)) }
        if (result is NetworkResult.Success) saveSession(result.data)
        return result
    }

    suspend fun staffLogin(email: String, password: String): NetworkResult<AuthResponse> {
        val result = safeApiCall { api.staffLogin(LoginRequest(email, password)) }
        if (result is NetworkResult.Success) saveSession(result.data)
        return result
    }

    suspend fun memberLogin(email: String, password: String): NetworkResult<AuthResponse> {
        val result = safeApiCall { api.memberLogin(LoginRequest(email, password)) }
        if (result is NetworkResult.Success) saveSession(result.data)
        return result
    }

    private fun saveSession(response: AuthResponse) {
        tokenManager.saveToken(response.token)
        tokenManager.saveTenantId(response.user.tenantId)
        tokenManager.saveRole(response.user.role)
        tokenManager.saveUserInfo(
            name      = response.user.name,
            email     = response.user.email ?: "",
            avatarUrl = null,
        )
    }

    suspend fun createGym(request: CreateGymRequest): NetworkResult<CreateGymResponse> {
        val result = safeApiCall { api.createGym(request) }
        if (result is NetworkResult.Success) {
            tokenManager.saveToken(result.data.token)
            tokenManager.saveTenantId(result.data.tenant.id)
            result.data.tenant.currencySymbol.takeIf { it.isNotBlank() }
                ?.let { tokenManager.saveCurrencySymbol(it) }
        }
        return result
    }

    fun logout() = tokenManager.clearAll()
    fun isLoggedIn() = tokenManager.isLoggedIn()
    fun hasCompletedOnboarding() = tokenManager.hasCompletedOnboarding()
    fun getRole() = tokenManager.getRole()
}
