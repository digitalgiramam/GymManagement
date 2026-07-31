package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class PaymentRepository(private val api: ApiService) : BaseRepository() {

    // ── Payment Methods ───────────────────────────────────────────────────────

    suspend fun getPaymentMethods(): NetworkResult<List<PaymentMethod>> =
        safeApiCall { api.getPaymentMethods() }

    suspend fun createPaymentMethod(name: String): NetworkResult<PaymentMethod> =
        safeApiCall { api.createPaymentMethod(CreatePaymentMethodRequest(name)) }

    // ── Payments ──────────────────────────────────────────────────────────────

    suspend fun getPayments(
        memberId: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): NetworkResult<List<Payment>> =
        safeApiCall { api.getPayments(memberId, startDate, endDate) }

    suspend fun createPayment(request: CreatePaymentRequest): NetworkResult<Payment> =
        safeApiCall { api.createPayment(request) }

    suspend fun updatePayment(id: Int, request: UpdatePaymentRequest): NetworkResult<Payment> =
        safeApiCall { api.updatePayment(id, request) }

    suspend fun deletePayment(id: Int): NetworkResult<Unit> =
        safeApiCall { api.deletePayment(id) }

    // ── Expiring Members ──────────────────────────────────────────────────────

    suspend fun getExpiringMembers(days: Int = 30): NetworkResult<List<Member>> =
        safeApiCall { api.getExpiringMembers(days) }
}
