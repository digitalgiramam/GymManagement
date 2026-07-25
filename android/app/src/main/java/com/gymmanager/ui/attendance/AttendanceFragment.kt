package com.gymmanager.ui.attendance

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.Member
import com.gymmanager.databinding.DialogCheckInBinding
import com.gymmanager.databinding.FragmentAttendanceBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.*

class AttendanceFragment : Fragment() {

    private var _binding: FragmentAttendanceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AttendanceViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AttendanceViewModel(
                    requireContext().gymApp.attendanceRepository,
                    requireContext().gymApp.memberRepository,
                ) as T
            }
        })[AttendanceViewModel::class.java]
    }

    private val adapter = AttendanceAdapter()
    private var memberList: List<Member> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAttendance.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAttendance() }
        binding.fabCheckIn.setOnClickListener { showCheckInDialog() }

        viewModel.members.observe(viewLifecycleOwner) { memberList = it }

        viewModel.attendance.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    adapter.submitList(result.data)
                    binding.tvCheckInCount.text =
                        getString(R.string.label_todays_checkins, result.data.size)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.checkInResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success ->
                    binding.root.showSnackbar(
                        getString(R.string.msg_checked_in, result.data.member?.fullName ?: "Member")
                    )
                is NetworkResult.Error ->
                    binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    private fun showCheckInDialog() {
        if (memberList.isEmpty()) {
            binding.root.showSnackbarError("No members found.")
            return
        }

        val activeMembers = memberList.filter { it.status == "Active" }
        if (activeMembers.isEmpty()) {
            binding.root.showSnackbarError("No active members to check in.")
            return
        }

        val dialogBinding = DialogCheckInBinding.inflate(layoutInflater)
        val names = activeMembers.map { "${it.fullName} (${it.phone})" }
        val spinnerAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        dialogBinding.spinnerMember.adapter = spinnerAdapter

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_check_in)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_check_in) { _, _ ->
                val selected = activeMembers[dialogBinding.spinnerMember.selectedItemPosition]
                viewModel.checkIn(selected.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
