package com.gymmanager.ui.payments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.*
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

    private val _members = MutableLiveData<List<Member>>(emptyList())
    val members: LiveData<List<Member>> = _members

    private val _paymentMethods = MutableLiveData<List<PaymentMethod>>(emptyList())
    val paymentMethods: LiveData<List<PaymentMethod>> = _paymentMethods

    private val _addResult = MutableLiveData<NetworkResult<Payment>>()
    val addResult: LiveData<NetworkResult<Payment>> = _addResult

    private val _editResult = MutableLiveData<NetworkResult<Payment>>()
    val editResult: LiveData<NetworkResult<Payment>> = _editResult

    private val _deleteResult = MutableLiveData<NetworkResult<Unit>>()
    val deleteResult: LiveData<NetworkResult<Unit>> = _deleteResult

    private val _walletBalance = MutableLiveData<NetworkResult<WalletBalance>>()
    val walletBalance: LiveData<NetworkResult<WalletBalance>> = _walletBalance

    init {
        loadPayments()
        loadMembers()
        loadPaymentMethods()
    }

    fun loadPayments() {
        _payments.value = NetworkResult.Loading
        viewModelScope.launch { _payments.value = paymentRepo.getPayments() }
    }

    fun loadMembers() {
        viewModelScope.launch {
            val r = memberRepo.getMembers()
            if (r is NetworkResult.Success) _members.value = r.data
        }
    }

    fun loadPaymentMethods() {
        viewModelScope.launch {
            val r = paymentRepo.getPaymentMethods()
            if (r is NetworkResult.Success) _paymentMethods.value = r.data
        }
    }

    fun loadWalletBalance(memberId: Int) {
        viewModelScope.launch {
            _walletBalance.value = paymentRepo.getMemberWallet(memberId)
        }
    }

    fun addPayment(request: CreatePaymentRequest) {
        _addResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = paymentRepo.createPayment(request)
            _addResult.value = result
            if (result is NetworkResult.Success) { loadPayments(); loadMembers() }
        }
    }

    fun editPayment(id: Int, request: UpdatePaymentRequest) {
        _editResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = paymentRepo.updatePayment(id, request)
            _editResult.value = result
            if (result is NetworkResult.Success) { loadPayments(); loadMembers() }
        }
    }

    fun deletePayment(id: Int) {
        _deleteResult.value = NetworkResult.Loading
        viewModelScope.launch {
            val result = paymentRepo.deletePayment(id)
            _deleteResult.value = result
            if (result is NetworkResult.Success) { loadPayments(); loadMembers() }
        }
    }
}
