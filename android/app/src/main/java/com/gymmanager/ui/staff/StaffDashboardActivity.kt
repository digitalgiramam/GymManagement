package com.gymmanager.ui.staff

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.databinding.ActivityStaffDashboardBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.auth.LoginActivity
import com.gymmanager.data.model.Member
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError

/**
 * Simplified dashboard for staff members (RECEPTIONIST / TRAINER).
 *
 * - TRAINER: loads only their assigned members via [StaffPortalMemberViewModel]
 * - RECEPTIONIST: loads all tenant members via [StaffMemberViewModel]
 * Both roles can mark attendance and view today's check-ins.
 */
class StaffDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStaffDashboardBinding

    private val isTrainer by lazy { gymApp.tokenManager.isTrainer() }

    // ── Trainer path: filtered member list from staff-portal ─────────────────
    private val portalMemberViewModel: StaffPortalMemberViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StaffPortalMemberViewModel(gymApp.staffPortalRepository) as T
            }
        })[StaffPortalMemberViewModel::class.java]
    }

    // ── Receptionist path: all members ────────────────────────────────────────
    private val memberViewModel: StaffMemberViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StaffMemberViewModel(gymApp.memberRepository) as T
            }
        })[StaffMemberViewModel::class.java]
    }

    private val attendanceViewModel: StaffAttendanceViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StaffAttendanceViewModel(gymApp.attendanceRepository) as T
            }
        })[StaffAttendanceViewModel::class.java]
    }

    private val memberAdapter    = StaffMemberListAdapter()
    private val attendanceAdapter = StaffAttendanceAdapter()

    /** Convenience: live member data regardless of role */
    private val activeMemberLiveData: LiveData<NetworkResult<List<Member>>>
        get() = if (isTrainer) portalMemberViewModel.members else memberViewModel.members

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStaffDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val staffName = gymApp.tokenManager.getUserName() ?: "Staff"
        val roleLabel = when {
            gymApp.tokenManager.isTrainer()      -> "Trainer"
            gymApp.tokenManager.isReceptionist() -> "Receptionist"
            else                                  -> "Staff"
        }
        binding.tvWelcome.text = "Welcome, $staffName ($roleLabel)"

        binding.rvMembers.adapter    = memberAdapter
        binding.rvAttendance.adapter = attendanceAdapter

        binding.btnCheckIn.setOnClickListener { showCheckInDialog() }
        binding.btnLogout.setOnClickListener  { logout() }
        binding.swipeRefresh.setOnRefreshListener { loadData() }

        observeViewModels()
        loadData()
    }

    private fun loadData() {
        if (isTrainer) portalMemberViewModel.loadMembers() else memberViewModel.loadMembers()
        attendanceViewModel.loadTodayAttendance()
    }

    private fun observeViewModels() {
        activeMemberLiveData.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Success -> memberAdapter.submitList(result.data)
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        attendanceViewModel.todayAttendance.observe(this) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    attendanceAdapter.submitList(result.data)
                    binding.tvTodayCount.text = "Today: ${result.data.size} check-ins"
                }
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        attendanceViewModel.checkInResult.observe(this) { result ->
            when (result) {
                is NetworkResult.Success -> {
                    binding.root.showSnackbar("Check-in recorded!")
                    attendanceViewModel.loadTodayAttendance()
                }
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    private fun showCheckInDialog() {
        val members = (activeMemberLiveData.value as? NetworkResult.Success)?.data
        if (members.isNullOrEmpty()) {
            binding.root.showSnackbarError("No members loaded yet.")
            return
        }
        val names = members.map { "${it.fullName} (${it.phone})" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Mark Attendance")
            .setItems(names) { _, index ->
                attendanceViewModel.checkIn(members[index].id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        gymApp.authRepository.logout()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
