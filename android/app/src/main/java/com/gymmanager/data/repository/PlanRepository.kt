package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class PlanRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getPlans(): NetworkResult<List<Plan>> =
        safeApiCall { api.getPlans() }

    suspend fun createPlan(request: CreatePlanRequest): NetworkResult<Plan> =
        safeApiCall { api.createPlan(request) }
}
