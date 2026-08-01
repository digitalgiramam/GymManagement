package com.gymmanager.ui.members

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.MemberDetail
import com.gymmanager.data.model.Plan
import com.gymmanager.data.model.Staff
import com.gymmanager.data.model.UpdateMemberRequest
import com.gymmanager.databinding.DialogEditMemberBinding
import com.gymmanager.databinding.FragmentMemberDetailBinding
import com.gymmanager.gymApp
import com.gymmanager.ui.attendance.AttendanceAdapter
import com.gymmanager.ui.member.MemberPaymentAdapter
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
                val app = requireContext().gymApp
                @Suppress("UNCHECKED_CAST")
                return MemberDetailViewModel(
                    app.memberRepository,
                    app.planRepository,
                    app.paymentRepository,
                    app.staffRepository,
                ) as T
            }
        })[MemberDetailViewModel::class.java]
    }

    private val attendanceAdapter = AttendanceAdapter()
    private val paymentAdapter    = MemberPaymentAdapter()

    /** Cached member for the edit dialog */
    private var currentMember: MemberDetail? = null
    private var availablePlans: List<Plan>     = emptyList()
    private var availableTrainers: List<Staff> = emptyList()

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

        binding.rvDetailPayments.apply {
            adapter = paymentAdapter
            layoutManager = LinearLayoutManager(requireContext())
            isNestedScrollingEnabled = false
        }

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadMember(memberId) }

        binding.btnEdit.setOnClickListener {
            val m = currentMember ?: return@setOnClickListener
            showEditDialog(m)
        }

        // "Add" button navigates to Payments tab with this member pre-selected
        binding.btnAddPayment.setOnClickListener {
            // Navigate to payments fragment — pass memberId so it can pre-select
            findNavController().navigate(R.id.paymentsFragment)
        }

        viewModel.plans.observe(viewLifecycleOwner)    { availablePlans = it }
        viewModel.trainers.observe(viewLifecycleOwner) { availableTrainers = it }

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

        viewModel.payments.observe(viewLifecycleOwner) { result ->
            if (result is NetworkResult.Success) {
                val list = result.data
                paymentAdapter.submitList(list)
                binding.tvNoDetailPayments.visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
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
        val symbol = requireContext().gymApp.tokenManager.getCurrencySymbol()

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

        // ── Payment Status card ─────────────────────────────────────────────
        val payStatus = m.paymentStatus ?: "Not Paid"
        binding.tvDetailPaymentBadge.text = payStatus
        binding.tvDetailPaymentBadge.setBackgroundResource(
            when (payStatus) {
                "Full Paid"    -> R.drawable.bg_status_active
                "Partial Paid" -> R.drawable.bg_status_partial
                else           -> R.drawable.bg_status_overdue
            }
        )

        binding.tvDetailLastAmount.text =
            m.lastPaymentAmount?.toCurrencyString(symbol) ?: "—"
        binding.tvDetailPlanFee.text =
            m.lastPlanFee?.toCurrencyString(symbol) ?: "—"

        val balance = m.overdueAmount ?: 0.0
        if (payStatus == "Partial Paid" && balance > 0) {
            binding.rowDetailBalance.visibility = View.VISIBLE
            binding.tvDetailBalance.text = balance.toCurrencyString(symbol)
        } else {
            binding.rowDetailBalance.visibility = View.GONE
        }

        // Subscription valid-until (membership expiry)
        val expiryIso = m.membershipExpiry
        if (expiryIso != null) {
            binding.tvDetailPaymentExpiry.text = formatDate(expiryIso)
            val isExpired = try {
                val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                df.timeZone = TimeZone.getTimeZone("UTC")
                !(df.parse(expiryIso) ?: Date(0)).after(Date())
            } catch (_: Exception) { true }
            binding.tvDetailPaymentExpiry.setTextColor(
                if (isExpired) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
            )
        } else {
            binding.tvDetailPaymentExpiry.text = "—"
        }

        attendanceAdapter.submitList(m.attendance)
    }

    private fun formatDate(iso: String): String = try {
        val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .also { it.timeZone = TimeZone.getTimeZone("UTC") }
        val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val display  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = runCatching { parserZ.parse(iso)!! }.getOrNull() ?: parserMs.parse(iso)!!
        display.format(date)
    } catch (_: Exception) { iso.take(10) }

    // ── Edit dialog ────────────────────────────────────────────────────────
    private fun showEditDialog(m: MemberDetail) {
        if (availablePlans.isEmpty()) {
            binding.root.showSnackbarError("Plans not loaded yet — try again.")
            return
        }

        val db      = DialogEditMemberBinding.inflate(layoutInflater)
        val isoFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        val dispFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        db.etFullName.setText(m.fullName)
        db.etPhone.setText(m.phone)
        db.etEmail.setText(m.email ?: "")
        db.etLocation.setText(m.location ?: "")
        db.switchActive.isChecked = m.status == "Active"

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        try { cal.time = isoFmt.parse(m.joinDate) ?: Date() } catch (_: Exception) {}
        db.etJoinDate.setText(dispFmt.format(cal.time))

        val planNames = availablePlans.map { "${it.name} (${it.durationDays}d)" }
        db.spinnerPlan.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, planNames,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val planIdx = availablePlans.indexOfFirst { it.id == m.planId }.coerceAtLeast(0)
        db.spinnerPlan.setSelection(planIdx)

        // Trainer spinner: "(No trainer)" + each TRAINER staff member
        val trainerEntries = listOf("(No trainer)") + availableTrainers.map { it.fullName }
        db.spinnerTrainer.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, trainerEntries,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val trainerIdx = if (m.trainerId == null) 0
            else (availableTrainers.indexOfFirst { it.id == m.trainerId } + 1).coerceAtLeast(0)
        db.spinnerTrainer.setSelection(trainerIdx)

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
                val email     = db.etEmail.text?.toString()?.trim()
                val location  = db.etLocation.text?.toString()?.trim()
                val status    = if (db.switchActive.isChecked) "Active" else "Inactive"
                val plan      = availablePlans[db.spinnerPlan.selectedItemPosition]
                val trainerPos = db.spinnerTrainer.selectedItemPosition
                val trainerId  = if (trainerPos == 0) null
                    else availableTrainers.getOrNull(trainerPos - 1)?.id
                val password   = db.etPassword.text?.toString()?.trim()?.ifBlank { null }

                viewModel.updateMember(
                    m.id,
                    UpdateMemberRequest(
                        fullName  = fullName,
                        phone     = phone,
                        email     = email?.ifBlank { null },
                        location  = location?.ifBlank { null },
                        planId    = plan.id,
                        status    = status,
                        joinDate  = isoFmt.format(cal.time),
                        trainerId = trainerId,
                        password  = password,
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
