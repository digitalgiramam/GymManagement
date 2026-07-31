package com.gymmanager.ui.payments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.*
import com.gymmanager.data.repository.PaymentRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class PaymentsViewModel(private val repository: PaymentRepository) : ViewModel() {

    private val _payments = MutableLiveData<List<Payment>>(emptyList())
    val payments: LiveData<List<Payment>> = _payments

    private val _paymentMethods = MutableLiveData<List<PaymentMethod>>(emptyList())
    val paymentMethods: LiveData<List<PaymentMethod>> = _paymentMethods

    private val _expiringMembers = MutableLiveData<List<Member>>(emptyList())
    val expiringMembers: LiveData<List<Member>> = _expiringMembers

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _actionResult = MutableLiveData<NetworkResult<Any>?>()
    val actionResult: LiveData<NetworkResult<Any>?> = _actionResult

    init {
        loadPaymentMethods()
        loadPayments()
        loadExpiringMembers()
    }

    fun loadPayments(memberId: Int? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.getPayments(memberId)) {
                is NetworkResult.Success -> {
                    _payments.value = result.data
                    _error.value = null
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun loadPaymentMethods() {
        viewModelScope.launch {
            when (val result = repository.getPaymentMethods()) {
                is NetworkResult.Success -> _paymentMethods.value = result.data
                else -> {}
            }
        }
    }

    fun loadExpiringMembers(days: Int = 30) {
        viewModelScope.launch {
            when (val result = repository.getExpiringMembers(days)) {
                is NetworkResult.Success -> _expiringMembers.value = result.data
                else -> {}
            }
        }
    }

    fun createPayment(request: CreatePaymentRequest) {
        viewModelScope.launch {
            val result = repository.createPayment(request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) {
                loadPayments()
                loadExpiringMembers()
            }
        }
    }

    fun updatePayment(id: Int, request: UpdatePaymentRequest) {
        viewModelScope.launch {
            val result = repository.updatePayment(id, request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadPayments()
        }
    }

    fun deletePayment(id: Int) {
        viewModelScope.launch {
            val result = repository.deletePayment(id)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) {
                loadPayments()
                loadExpiringMembers()
            }
        }
    }

    fun clearActionResult() { _actionResult.value = null }
}
