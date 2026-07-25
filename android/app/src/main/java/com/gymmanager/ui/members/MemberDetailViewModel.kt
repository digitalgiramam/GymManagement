package com.gymmanager.ui.members

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.MemberDetail
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class MemberDetailViewModel(private val repository: MemberRepository) : ViewModel() {

    private val _member = MutableLiveData<NetworkResult<MemberDetail>>()
    val member: LiveData<NetworkResult<MemberDetail>> = _member

    private val _updateResult = MutableLiveData<NetworkResult<Any>>()
    val updateResult: LiveData<NetworkResult<Any>> = _updateResult

    fun loadMember(id: Int) {
        _member.value = NetworkResult.Loading
        viewModelScope.launch {
            _member.value = repository.getMemberDetail(id)
        }
    }

    fun toggleStatus(id: Int, currentStatus: String) {
        val newStatus = if (currentStatus == "Active") "Inactive" else "Active"
        viewModelScope.launch {
            val result = repository.updateMember(id, UpdateMemberRequest(
                fullName = null, phone = null, email = null, planId = null, status = newStatus
            ))
            @Suppress("UNCHECKED_CAST")
            _updateResult.value = result as NetworkResult<Any>
            if (result is NetworkResult.Success) loadMember(id)
        }
    }
}
