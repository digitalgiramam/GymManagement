package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.Attendance
import com.gymmanager.data.model.CheckInRequest
import com.gymmanager.data.model.Member
import com.gymmanager.utils.NetworkResult

/**
 * Repository for TRAINER-specific endpoints.
 * Trainers see only members assigned to them via [Member.trainerId].
 */
class StaffPortalRepository(private val api: ApiService) : BaseRepository() {

    /** Returns members assigned to the logged-in trainer (filtered server-side by staffId). */
    suspend fun getMyMembers(): NetworkResult<List<Member>> =
        safeApiCall { api.getMyMembers() }

    /** Today's attendance for all of this trainer's assigned members. */
    suspend fun getMyAttendance(): NetworkResult<List<Attendance>> =
        safeApiCall { api.getMyAttendance() }

    /** Mark check-in for any member in this tenant. */
    suspend fun checkIn(memberId: Int): NetworkResult<Attendance> =
        safeApiCall { api.staffPortalCheckIn(CheckInRequest(memberId)) }
}
