package com.gymmanager.ui.members

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.MemberDetail
import com.gymmanager.data.model.Plan
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.databinding.DialogEditMemberBinding
import com.gymmanager.databinding.FragmentMemberDetailBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.attendance.AttendanceAdapter
import com.gymmanager.utils.*
import java.text.SimpleDateFormat
import java.util.*

class MemberDetailFragment : Fragment() {

    private var _binding: FragmentMemberDetailBinding? = null
    private val binding get() = _binding!!

    private val memberId by lazy { requireArguments().getInt("memberId") }

    private val viewModel: MemberDetailViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemberDetailViewModel(
                    requireContext().gymApp.memberRepository,
                    requireContext().gymApp.planRepository,
                ) as T
            }
        })[MemberDetailViewModel::class.java]
    }

    private val attendanceAdapter = AttendanceAdapter()

    /** Cached member for the edit dialog */
    private var currentMember: MemberDetail? = null
    private var availablePlans: List<Plan> = emptyList()

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

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadMember(memberId) }

        binding.btnEdit.setOnClickListener {
            val m = currentMember ?: return@setOnClickListener
            showEditDialog(m)
        }

        viewModel.plans.observe(viewLifecycleOwner) { availablePlans = it }

        viewModel.member.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    currentMember = result.data
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
                is NetworkResult.Success ->
                    binding.root.showSnackbar(getString(R.string.msg_member_updated))
                is NetworkResult.Error ->
                    binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        viewModel.loadMember(memberId)
    }

    private fun bindMember(m: MemberDetail) {
        binding.tvName.text     = m.fullName
        binding.tvPhone.text    = m.phone
        binding.tvEmail.text    = m.email ?: "—"
        binding.tvLocation.text = if (!m.location.isNullOrBlank()) "📍 ${m.location}" else ""
        binding.tvPlan.text     = m.plan?.name ?: "—"
        binding.tvJoinDate.text = m.joinDate.toDisplayDate()
        binding.tvStatus.text   = m.status

        val statusColor = if (m.status == "Active") R.color.status_active else R.color.status_inactive
        binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), statusColor))

        // Membership expiry
        val days = m.daysUntilExpiry
        binding.tvMembershipExpiry.text = when {
            days == null -> "—"
            days < 0     -> "Expired ${-days}d ago"
            days == 0    -> "Expires today"
            else         -> "Valid · ${days}d left"
        }
        val expiryColor = when {
            days == null || days > 7 -> R.color.expiry_ok
            days in 1..7             -> R.color.expiry_warning
            else                     -> R.color.expiry_expired
        }
        binding.tvMembershipExpiry.setTextColor(ContextCompat.getColor(requireContext(), expiryColor))

        binding.btnToggleStatus.text = if (m.status == "Active")
            getString(R.string.action_deactivate) else getString(R.string.action_activate)
        binding.btnToggleStatus.setOnClickListener {
            viewModel.toggleStatus(m.id, m.status)
        }

        attendanceAdapter.submitList(m.attendance)
    }

    // ── Edit dialog with date picker ───────────────────────────────────────
    private fun showEditDialog(m: MemberDetail) {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("Plans not loaded yet — try again.")
            return
        }

        val db      = DialogEditMemberBinding.inflate(layoutInflater)
        val isoFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dispFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        // Pre-fill fields
        db.etFullName.setText(m.fullName)
        db.etPhone.setText(m.phone)
        db.etEmail.setText(m.email ?: "")
        db.etLocation.setText(m.location ?: "")
        db.switchActive.isChecked = m.status == "Active"

        // Parse current joinDate into a Calendar
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        try { cal.time = isoFmt.parse(m.joinDate) ?: Date() } catch (_: Exception) {}
        db.etJoinDate.setText(dispFmt.format(cal.time))

        // Plan spinner — pre-select current plan
        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d)" }
        db.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val planIdx = availablePlans.indexOfFirst { it.id == m.planId }.coerceAtLeast(0)
        db.spinnerPlan.setSelection(planIdx)

        // Date picker — opens when user taps the join date field
        db.etJoinDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    cal.set(year, month, day, 0, 0, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    db.etJoinDate.setText(dispFmt.format(cal.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_edit_member)
            .setView(db.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val fullName = db.etFullName.text?.toString()?.trim() ?: ""
                val phone    = db.etPhone.text?.toString()?.trim() ?: ""
                if (fullName.isBlank() || phone.isBlank()) {
                    binding.root.showSnackbarError("Name and phone are required.")
                    return@setPositiveButton
                }
                val email    = db.etEmail.text?.toString()?.trim()
                val location = db.etLocation.text?.toString()?.trim()
                val status   = if (db.switchActive.isChecked) "Active" else "Inactive"
                val plan     = availablePlans[db.spinnerPlan.selectedItemPosition]

                viewModel.updateMember(
                    m.id,
                    UpdateMemberRequest(
                        fullName = fullName,
                        phone    = phone,
                        email    = email?.ifBlank { null },
                        location = location?.ifBlank { null },
                        planId   = plan.id,
                        status   = status,
                        joinDate = isoFmt.format(cal.time),
                    )
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
