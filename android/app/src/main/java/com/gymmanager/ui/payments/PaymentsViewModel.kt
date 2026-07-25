package com.gymmanager.ui.payments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.CreatePaymentRequest
import com.gymmanager.data.model.Member
import com.gymmanager.data.model.Payment
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.data.repository.PaymentRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class PaymentsViewModel(
    private val paymentRepo: PaymentRepository,
    private val memberRepo: MemberRepository,
) : ViewModel() {

    private val _payments = MutableLiveData<NetworkResult<List<Payment>>>()
    val payments: LiveData<NetworkResult<List<Payment>>> = _payments

    private val _members = MutableLiveData<List<Member>>()
    val members: LiveData<List<Member>> = _members

    private val _addResult = MutableLiveData<NetworkResult<Payment>>()
    val addResult: LiveData<NetworkResult<Payment>> = _addResult

    init {
        loadPayments()
        loadMembers()
    }

    fun loadPayments() {
        _payments.value = NetworkResult.Loading
        viewModelScope.launch {
            _payments.value = paymentRepo.getPayments()
        }
    }

    fun loadMembers() {
        viewModelScope.launch {
            val r = memberRepo.getMembers()
            if (r is NetworkResult.Success) _members.value = r.data
        }
    }

    fun addPayment(request: CreatePaymentRequest) {
        _addResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = paymentRepo.createPayment(request)
            _addResult.value = result
            if (result is NetworkResult.Success) loadPayments()
        }
    }
}
