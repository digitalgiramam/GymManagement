package com.gymmanager.ui.members

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.MemberDetail
import com.gymmanager.data.model.Payment
import com.gymmanager.data.model.Plan
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.data.repository.PaymentRepository
import com.gymmanager.data.repository.PlanRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class MemberDetailViewModel(
    private val repository: MemberRepository,
    private val planRepository: PlanRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _member = MutableLiveData<NetworkResult<MemberDetail>>()
    val member: LiveData<NetworkResult<MemberDetail>> = _member

    private val _plans = MutableLiveData<List<Plan>>(emptyList())
    val plans: LiveData<List<Plan>> = _plans

    private val _updateResult = MutableLiveData<NetworkResult<Any>>()
    val updateResult: LiveData<NetworkResult<Any>> = _updateResult

    private val _payments = MutableLiveData<NetworkResult<List<Payment>>>()
    val payments: LiveData<NetworkResult<List<Payment>>> = _payments

    init { loadPlans() }

    fun loadMember(id: Int) {
        _member.value = NetworkResult.Loading
        viewModelScope.launch {
            _member.value = repository.getMemberDetail(id)
            // Load payments in parallel
            _payments.value = paymentRepository.getPayments(memberId = id)
        }
    }

    private fun loadPlans() {
        viewModelScope.launch {
            val result = planRepository.getPlans()
            if (result is NetworkResult.Success) _plans.value = result.data
        }
    }

    fun updateMember(id: Int, request: UpdateMemberRequest) {
        viewModelScope.launch {
            val result = repository.updateMember(id, request)
            @Suppress("UNCHECKED_CAST")
            _updateResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadMember(id)
        }
    }

    fun toggleStatus(id: Int, currentStatus: String) {
        val newStatus = if (currentStatus == "Active") "Inactive" else "Active"
        viewModelScope.launch {
            val result = repository.updateMember(id, UpdateMemberRequest(
                fullName = null, phone = null, email = null, location = null,
                planId = null, status = newStatus, joinDate = null,
            ))
            @Suppress("UNCHECKED_CAST")
            _updateResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadMember(id)
        }
    }
}
