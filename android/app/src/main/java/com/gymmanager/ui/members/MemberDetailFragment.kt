package com.gymmanager.ui.members

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.gymmanager.R
import com.gymmanager.data.model.MemberDetail
import com.gymmanager.databinding.FragmentMemberDetailBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.attendance.AttendanceAdapter
import com.gymmanager.ui.payments.PaymentsAdapter
import com.gymmanager.utils.*

class MemberDetailFragment : Fragment() {

    private var _binding: FragmentMemberDetailBinding? = null
    private val binding get() = _binding!!

    private val memberId by lazy { requireArguments().getInt("memberId") }

    private val viewModel: MemberDetailViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemberDetailViewModel(requireContext().gymApp.memberRepository) as T
            }
        })[MemberDetailViewModel::class.java]
    }

    private val attendanceAdapter = AttendanceAdapter()
    private val paymentsAdapter   = PaymentsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMemberDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAttendance.apply {
            adapter = attendanceAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }
        binding.rvPayments.apply {
            adapter = paymentsAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadMember(memberId) }

        viewModel.member.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    bindMember(result.data)
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Status updated")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        viewModel.loadMember(memberId)
    }

    private fun bindMember(m: MemberDetail) {
        binding.tvName.text     = m.fullName
        binding.tvPhone.text    = m.phone
        binding.tvEmail.text    = m.email ?: "—"
        binding.tvPlan.text     = m.plan?.name ?: "—"
        binding.tvJoinDate.text = m.joinDate.toDisplayDate()
        binding.tvStatus.text   = m.status

        val statusColor = if (m.status == "Active") R.color.status_active else R.color.status_inactive
        binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))

        binding.btnToggleStatus.text = if (m.status == "Active")
            getString(R.string.action_deactivate) else getString(R.string.action_activate)
        binding.btnToggleStatus.setOnClickListener {
            viewModel.toggleStatus(m.id, m.status)
        }

        attendanceAdapter.submitList(m.attendance)
        paymentsAdapter.submitList(m.payments)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
