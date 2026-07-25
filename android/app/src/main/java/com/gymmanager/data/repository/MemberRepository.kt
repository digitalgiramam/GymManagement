package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class MemberRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getMembers(search: String? = null) =
        safeApiCall { api.getMembers(search) }

    suspend fun getMemberDetail(id: Int): NetworkResult<MemberDetail> =
        safeApiCall { api.getMemberDetail(id) }

    suspend fun createMember(request: CreateMemberRequest): NetworkResult<Member> =
        safeApiCall { api.createMember(request) }

    suspend fun updateMember(id: Int, request: UpdateMemberRequest): NetworkResult<Member> =
        safeApiCall { api.updateMember(id, request) }

    suspend fun deleteMember(id: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteMember(id) }
}
