package com.gymmanager.ui.staff

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Member
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

class StaffMemberViewModel(private val repository: MemberRepository) : ViewModel() {

    private val _members = MutableLiveData<NetworkResult<List<Member>>>()
    val members: LiveData<NetworkResult<List<Member>>> = _members

    init { loadMembers() }

    fun loadMembers() {
        _members.value = NetworkResult.Loading
        viewModelScope.launch {
            _members.value = repository.getMembers()
        }
    }
}
