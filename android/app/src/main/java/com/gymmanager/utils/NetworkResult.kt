package com.gymmanager.utils

/**
 * Sealed class representing the three states of an async API operation.
 * All ViewModels expose LiveData<NetworkResult<T>> to the UI layer.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int = 0) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
