package com.gymmanager.ui.expenses

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Expense
import com.gymmanager.data.model.ExpenseCategory
import com.gymmanager.data.model.CreateExpenseRequest
import com.gymmanager.data.repository.ExpenseRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class ExpensesViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _expenses = MutableLiveData<List<Expense>>(emptyList())
    val expenses: LiveData<List<Expense>> = _expenses

    private val _categories = MutableLiveData<List<ExpenseCategory>>(emptyList())
    val categories: LiveData<List<ExpenseCategory>> = _categories

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _actionResult = MutableLiveData<NetworkResult<Any>?>()
    val actionResult: LiveData<NetworkResult<Any>?> = _actionResult

    init {
        loadCategories()
        loadExpenses()
    }

    fun loadExpenses(categoryId: Int? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = repository.getExpenses(categoryId)) {
                is NetworkResult.Success -> {
                    _expenses.value = result.data
                    _error.value = null
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            when (val result = repository.getCategories()) {
                is NetworkResult.Success -> _categories.value = result.data
                else -> {}
            }
        }
    }

    fun addExpense(request: CreateExpenseRequest) {
        viewModelScope.launch {
            val result = repository.createExpense(request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadExpenses()
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            val result = repository.deleteExpense(id)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadExpenses()
        }
    }

    fun clearActionResult() { _actionResult.value = null }
}
