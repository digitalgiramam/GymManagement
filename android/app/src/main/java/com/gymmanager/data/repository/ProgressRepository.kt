package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.Goal
import com.gymmanager.data.model.GoalRequest
import com.gymmanager.data.model.GoalStatusRequest
import com.gymmanager.data.model.ProgressEntry
import com.gymmanager.data.model.ProgressEntryRequest
import com.gymmanager.utils.NetworkResult

/**
 * Wraps both flavors of progress-tracking endpoints:
 *  - "member" functions: staff/trainer viewing & recording on behalf of a specific member
 *  - "my" functions: a logged-in member managing their own progress via the Member Portal
 */
class ProgressRepository(private val api: ApiService) : BaseRepository() {

    // ── Staff/trainer side — acting on a specific member ────────────────────
    suspend fun getProgress(memberId: Int): NetworkResult<List<ProgressEntry>> =
        safeApiCall { api.getMemberProgress(memberId) }

    suspend fun addProgress(memberId: Int, request: ProgressEntryRequest): NetworkResult<ProgressEntry> =
        safeApiCall { api.addMemberProgress(memberId, request) }

    suspend fun deleteProgress(memberId: Int, entryId: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteMemberProgress(memberId, entryId) }

    suspend fun getGoals(memberId: Int): NetworkResult<List<Goal>> =
        safeApiCall { api.getMemberGoals(memberId) }

    suspend fun addGoal(memberId: Int, request: GoalRequest): NetworkResult<Goal> =
        safeApiCall { api.addMemberGoal(memberId, request) }

    suspend fun updateGoalStatus(memberId: Int, goalId: Int, status: String): NetworkResult<Goal> =
        safeApiCall { api.updateMemberGoal(memberId, goalId, GoalStatusRequest(status)) }

    suspend fun deleteGoal(memberId: Int, goalId: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteMemberGoal(memberId, goalId) }

    // ── Member self-service side ─────────────────────────────────────────────
    suspend fun getMyProgress(): NetworkResult<List<ProgressEntry>> =
        safeApiCall { api.getMyProgress() }

    suspend fun addMyProgress(request: ProgressEntryRequest): NetworkResult<ProgressEntry> =
        safeApiCall { api.addMyProgress(request) }

    suspend fun deleteMyProgress(entryId: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteMyProgress(entryId) }

    suspend fun getMyGoals(): NetworkResult<List<Goal>> =
        safeApiCall { api.getMyGoals() }

    suspend fun addMyGoal(request: GoalRequest): NetworkResult<Goal> =
        safeApiCall { api.addMyGoal(request) }

    suspend fun updateMyGoalStatus(goalId: Int, status: String): NetworkResult<Goal> =
        safeApiCall { api.updateMyGoal(goalId, GoalStatusRequest(status)) }

    suspend fun deleteMyGoal(goalId: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteMyGoal(goalId) }
}
