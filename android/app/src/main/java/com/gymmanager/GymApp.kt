package com.gymmanager

import android.app.Application
import com.gymmanager.data.api.RetrofitClient
import com.gymmanager.data.local.TokenManager
import com.gymmanager.data.repository.*

/**
 * Application-level singleton that wires together the dependency graph manually
 * (no Hilt/Dagger required for this MVP — add them when the team scales).
 *
 * Repositories are lazily initialised so the first access triggers construction.
 */
class GymApp : Application() {

    val tokenManager by lazy { TokenManager(this) }
    private val apiService by lazy { RetrofitClient.create(tokenManager) }

    val authRepository       by lazy { AuthRepository(apiService, tokenManager) }
    val memberRepository     by lazy { MemberRepository(apiService) }
    val planRepository       by lazy { PlanRepository(apiService) }
    val attendanceRepository by lazy { AttendanceRepository(apiService) }
    val paymentRepository    by lazy { PaymentRepository(apiService) }
    val dashboardRepository  by lazy { DashboardRepository(apiService) }
}

/** Convenience extension to reach [GymApp] from any Fragment or Activity. */
val android.content.Context.gymApp: GymApp
    get() = applicationContext as GymApp
