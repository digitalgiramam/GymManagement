package com.gymmanager.ui.members

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.CreateMemberRequest
import com.gymmanager.data.model.Member
import com.gymmanager.data.model.Plan
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.data.repository.PlanRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class MembersViewModel(
    private val memberRepo: MemberRepository,
    private val planRepo: PlanRepository,
) : ViewModel() {

    private val _members = MutableLiveData<NetworkResult<List<Member>>>()
    val members: LiveData<NetworkResult<List<Member>>> = _members

    private val _plans = MutableLiveData<NetworkResult<List<Plan>>>()
    val plans: LiveData<NetworkResult<List<Plan>>> = _plans

    private val _actionResult = MutableLiveData<NetworkResult<Any>>()
    val actionResult: LiveData<NetworkResult<Any>> = _actionResult

    init {
        loadMembers()
        loadPlans()
    }

    fun loadMembers(search: String? = null) {
        _members.value = NetworkResult.Loading
        viewModelScope.launch {
            _members.value = memberRepo.getMembers(search?.ifBlank { null })
        }
    }

    fun loadPlans() {
        viewModelScope.launch {
            _plans.value = planRepo.getPlans()
        }
    }

    fun createMember(request: CreateMemberRequest) {
        _actionResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = memberRepo.createMember(request)
            _actionResult.value = result
            if (result is NetworkResult.Success) loadMembers()
        }
    }

    fun updateMember(id: Int, request: UpdateMemberRequest) {
        _actionResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = memberRepo.updateMember(id, request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadMembers()
        }
    }

    fun deleteMember(id: Int) {
        viewModelScope.launch {
            val result = memberRepo.deleteMember(id)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadMembers()
        }
    }
}
