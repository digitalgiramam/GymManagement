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

    /** Owner "forgot password" — step 1: request a 6-digit code emailed to them. */
    suspend fun forgotPassword(email: String): NetworkResult<ForgotPasswordResponse> =
        safeApiCall { api.forgotPassword(ForgotPasswordRequest(email)) }

    /** Owner "forgot password" — step 2: submit the emailed code + new password. */
    suspend fun resetPassword(email: String, code: String, password: String): NetworkResult<ResetPasswordResponse> =
        safeApiCall { api.resetPassword(ResetPasswordRequest(email, code, password)) }

    private fun saveSession(response: AuthResponse) {
        tokenManager.saveToken(response.token)
        tokenManager.saveTenantId(response.user.tenantId)
        tokenManager.saveRole(response.user.role)
        tokenManager.saveUserInfo(
            name      = response.user.name,
            email     = response.user.email ?: "",
            avatarUrl = null,
        )
        // Keep the gym's configured currency in sync for Staff/Member portals too,
        // not just the Owner flow (which sets it during onboarding).
        response.user.currencySymbol?.takeIf { it.isNotBlank() }
            ?.let { tokenManager.saveCurrencySymbol(it) }
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
