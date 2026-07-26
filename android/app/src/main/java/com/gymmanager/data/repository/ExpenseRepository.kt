package com.gymmanager.data.repository

import com.gymmanager.data.api.ApiService
import com.gymmanager.data.model.*
import com.gymmanager.utils.NetworkResult

class ExpenseRepository(private val api: ApiService) : BaseRepository() {

    suspend fun getCategories(): NetworkResult<List<ExpenseCategory>> =
        safeApiCall { api.getExpenseCategories() }

    suspend fun createCategory(name: String): NetworkResult<ExpenseCategory> =
        safeApiCall { api.createExpenseCategory(CreateExpenseCategoryRequest(name)) }

    suspend fun getExpenses(
        categoryId: Int? = null,
        startDate: String? = null,
        endDate: String? = null,
    ): NetworkResult<List<Expense>> =
        safeApiCall { api.getExpenses(categoryId, startDate, endDate) }

    suspend fun createExpense(request: CreateExpenseRequest): NetworkResult<Expense> =
        safeApiCall { api.createExpense(request) }

    suspend fun updateExpense(id: Int, request: CreateExpenseRequest): NetworkResult<Expense> =
        safeApiCall { api.updateExpense(id, request) }

    suspend fun deleteExpense(id: Int): NetworkResult<Unit> =
        safeApiCall { api.deleteExpense(id) }
}
