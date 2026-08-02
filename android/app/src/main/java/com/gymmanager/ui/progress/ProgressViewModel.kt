package com.gymmanager.ui.progress

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Goal
import com.gymmanager.data.model.GoalRequest
import com.gymmanager.data.model.ProgressEntry
import com.gymmanager.data.model.ProgressEntryRequest
import com.gymmanager.data.repository.ProgressRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

/**
 * Shared ViewModel for both usages of [ProgressActivity]:
 *  - Staff/trainer viewing & recording for a specific member (memberId >= 0)
 *  - A member viewing/logging their own progress (memberId == SELF)
 */
class ProgressViewModel(
    private val repository: ProgressRepository,
    private val memberId: Int,
) : ViewModel() {

    companion object {
        const val SELF = -1
    }

    private val isSelf get() = memberId == SELF

    private val _entries = MutableLiveData<NetworkResult<List<ProgressEntry>>>()
    val entries: LiveData<NetworkResult<List<ProgressEntry>>> = _entries

    private val _goals = MutableLiveData<NetworkResult<List<Goal>>>()
    val goals: LiveData<NetworkResult<List<Goal>>> = _goals

    private val _actionResult = MutableLiveData<NetworkResult<Any>?>()
    val actionResult: LiveData<NetworkResult<Any>?> = _actionResult

    fun load() {
        viewModelScope.launch {
            _entries.value = NetworkResult.Loading
            _entries.value = if (isSelf) repository.getMyProgress() else repository.getProgress(memberId)
        }
        viewModelScope.launch {
            _goals.value = NetworkResult.Loading
            _goals.value = if (isSelf) repository.getMyGoals() else repository.getGoals(memberId)
        }
    }

    fun addEntry(request: ProgressEntryRequest) {
        viewModelScope.launch {
            val result = if (isSelf) repository.addMyProgress(request) else repository.addProgress(memberId, request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) load()
        }
    }

    fun deleteEntry(entryId: Int) {
        viewModelScope.launch {
            val result = if (isSelf) repository.deleteMyProgress(entryId) else repository.deleteProgress(memberId, entryId)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) load()
        }
    }

    fun addGoal(request: GoalRequest) {
        viewModelScope.launch {
            val result = if (isSelf) repository.addMyGoal(request) else repository.addGoal(memberId, request)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) load()
        }
    }

    fun setGoalStatus(goalId: Int, status: String) {
        viewModelScope.launch {
            val result = if (isSelf) repository.updateMyGoalStatus(goalId, status)
                         else repository.updateGoalStatus(memberId, goalId, status)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) load()
        }
    }

    fun deleteGoal(goalId: Int) {
        viewModelScope.launch {
            val result = if (isSelf) repository.deleteMyGoal(goalId) else repository.deleteGoal(memberId, goalId)
            @Suppress("UNCHECKED_CAST")
            _actionResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) load()
        }
    }

    fun clearActionResult() { _actionResult.value = null }
}
