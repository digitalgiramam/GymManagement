package com.gymmanager.ui.staff

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymmanager.data.model.Member
import com.gymmanager.data.repository.StaffPortalRepository
import com.gymmanager.utils.NetworkResult
import kotlinx.coroutines.launch

/**
 * ViewModel for TRAINER role — loads only the members assigned to this trainer
 * via [StaffPortalRepository.getMyMembers()].
 */
class StaffPortalMemberViewModel(
    private val repository: StaffPortalRepository,
) : ViewModel() {

    private val _members = MutableLiveData<NetworkResult<List<Member>>>()
    val members: LiveData<NetworkResult<List<Member>>> = _members

    init { loadMembers() }

    fun loadMembers() {
        _members.value = NetworkResult.Loading
        viewModelScope.launch {
            _members.value = repository.getMyMembers()
        }
    }
}
