package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.CreatePaymentRequest
import com.gymmanager.data.model.Payment
import com.gymmanager.utils.NetworkResult

class PaymentRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getPayments(
        memberId: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): NetworkResult<List<Payment>> =
        safeApiCall { api.getPayments(memberId, startDate, endDate) }

    suspend fun createPayment(request: CreatePaymentRequest): NetworkResult<Payment> =
        safeApiCall { api.createPayment(request) }
}
