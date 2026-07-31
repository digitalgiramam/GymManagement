package com.gymmanager.ui.payments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.*
import com.gymmanager.databinding.DialogAddPaymentBinding
import com.gymmanager.databinding.FragmentPaymentsBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.*

class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaymentsViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PaymentsViewModel(
                    requireContext().gymApp.paymentRepository,
                    requireContext().gymApp.memberRepository,
                ) as T
            }
        })[PaymentsViewModel::class.java]
    }

    private val adapter by lazy {
        PaymentsAdapter(
            onEdit   = { showPaymentDialog(editPayment = it) },
            onDelete = { confirmDelete(it) },
        )
    }

    private var memberList: List<Member> = emptyList()
    private var methodList: List<PaymentMethod> = emptyList()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPayments.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadPayments() }
        binding.fabAddPayment.setOnClickListener { showPaymentDialog() }

        viewModel.members.observe(viewLifecycleOwner)        { memberList = it }
        viewModel.paymentMethods.observe(viewLifecycleOwner) { methodList = it }

        viewModel.payments.observe(viewLifecycleOwner) { result ->
            binding.swipeRefresh.isRefreshing = false
            when (result) {
                is NetworkResult.Loading -> binding.progressBar.show()
                is NetworkResult.Success -> {
                    binding.progressBar.hide()
                    adapter.submitList(result.data)
                    binding.tvEmpty.visibility =
                        if (result.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is NetworkResult.Error -> {
                    binding.progressBar.hide()
                    binding.root.showSnackbarError(result.message)
                }
            }
        }

        viewModel.addResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar(getString(R.string.msg_payment_added))
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        viewModel.editResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Payment updated")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Payment deleted")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPayments()
        viewModel.loadPaymentMethods()
        viewModel.loadMembers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Payment dialog (shared for Add and Edit) ───────────────────────────

    private fun showPaymentDialog(editPayment: Payment? = null) {
        if (memberList.isEmpty()) { binding.root.showSnackbarError("No members found."); return }
        if (methodList.isEmpty()) { binding.root.showSnackbarError("No payment methods configured."); return }

        val sym           = requireContext().gymApp.tokenManager.getCurrencySymbol()
        val isEditing     = editPayment != null
        val dialogBinding = DialogAddPaymentBinding.inflate(layoutInflater)

        // ── Member spinner ───────────────────────────────────────────────────
        val memberNames = memberList.map { "${it.fullName} (${it.phone})" }
        dialogBinding.spinnerMember.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, memberNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        // Disable member change while editing (wallet logic depends on the original member)
        dialogBinding.spinnerMember.isEnabled = !isEditing

        // Pre-select member when editing
        if (isEditing) {
            val idx = memberList.indexOfFirst { it.id == editPayment!!.memberId }
            if (idx >= 0) dialogBinding.spinnerMember.setSelection(idx)
        }

        // ── Method spinner ───────────────────────────────────────────────────
        val methodNames = methodList.map { it.name }
        dialogBinding.spinnerMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, methodNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        if (isEditing) {
            val idx = methodList.indexOfFirst { it.id == editPayment!!.methodId }
            if (idx >= 0) dialogBinding.spinnerMethod.setSelection(idx)
        }

        // ── Wallet projection helpers ────────────────────────────────────────

        fun updateWalletProjection(member: Member) {
            val walletNow = member.walletBalance
            val planFee   = member.plan?.fee ?: 0.0
            val cash      = dialogBinding.etAmount.text?.toString()?.toDoubleOrNull() ?: 0.0

            // Suggested = planFee - walletNow (clamped to ≥ 0)
            val suggested = (planFee - walletNow).coerceAtLeast(0.0)
            dialogBinding.tvSuggestedAmount.text = suggested.toCurrencyString(sym)

            // Wallet after = walletNow + (cash - planFee)
            val after = walletNow + (cash - planFee)
            dialogBinding.tvWalletProjection.text = after.toCurrencyString(sym)
            dialogBinding.tvWalletProjection.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (after >= 0) R.color.success else R.color.expiry_expired,
                )
            )
        }

        fun updateMemberCard(member: Member) {
            dialogBinding.tvMemberJoinDate.text = "Joined: ${member.joinDate.toDisplayDate()}"
            dialogBinding.tvMemberLastPaid.text = if (member.lastPaymentDate != null)
                "Last paid: ${member.lastPaymentDate.toDisplayDate()}" else "Last paid: never"

            val days = member.daysUntilExpiry
            dialogBinding.tvMemberExpiry.text = when {
                days == null -> ""
                days < 0    -> "Membership expired ${-days} day(s) ago"
                days == 0   -> "Membership expires today"
                else        -> "Membership valid for $days more day(s)"
            }
            dialogBinding.tvMemberExpiry.setTextColor(
                ContextCompat.getColor(requireContext(), when {
                    days == null || days > 7 -> R.color.expiry_ok
                    days in 1..7             -> R.color.expiry_warning
                    else                     -> R.color.expiry_expired
                })
            )

            val plan = member.plan
            dialogBinding.tvMemberPlanFee.text = if (plan != null)
                "Plan: ${plan.name}  •  Fee: ${plan.fee.toCurrencyString(sym)}  •  ${plan.durationDays} days"
            else ""

            // Wallet balance display (green = credit, red = debt)
            val wallet = member.walletBalance
            dialogBinding.tvWalletBalance.text = wallet.toCurrencyString(sym)
            dialogBinding.tvWalletBalance.setTextColor(
                ContextCompat.getColor(requireContext(),
                    if (wallet >= 0) R.color.success else R.color.expiry_expired)
            )

            // Pre-fill amount for new payments = planFee − wallet (suggested to collect)
            if (!isEditing && dialogBinding.etAmount.text.isNullOrBlank()) {
                val suggested = (plan?.fee?.minus(wallet))?.coerceAtLeast(0.0)
                if (suggested != null) dialogBinding.etAmount.setText("%.2f".format(suggested))
            }

            updateWalletProjection(member)
        }

        // ── Amount text watcher ──────────────────────────────────────────────
        dialogBinding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val member = memberList.getOrNull(dialogBinding.spinnerMember.selectedItemPosition)
                if (member != null) updateWalletProjection(member)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })

        // ── Member selection ─────────────────────────────────────────────────
        dialogBinding.spinnerMember.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) =
                    updateMemberCard(memberList[pos])
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }

        // Initial state
        val initialMemberIdx = if (isEditing)
            memberList.indexOfFirst { it.id == editPayment!!.memberId }.coerceAtLeast(0)
        else 0

        if (memberList.isNotEmpty()) {
            if (isEditing) {
                updateMemberCard(memberList[initialMemberIdx])
                dialogBinding.etAmount.setText("%.2f".format(editPayment!!.amount))
                dialogBinding.etNotes.setText(editPayment.notes ?: "")
            } else {
                updateMemberCard(memberList[0])
            }
        }

        // ── Dialog ───────────────────────────────────────────────────────────
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isEditing) "Edit Payment" else getString(R.string.title_add_payment))
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val amountStr = dialogBinding.etAmount.text?.toString()?.trim() ?: ""
                val amount    = amountStr.toDoubleOrNull()
                if (amount == null || amount < 0) {
                    binding.root.showSnackbarError("Enter a valid amount.")
                    return@setPositiveButton
                }
                val method = methodList[dialogBinding.spinnerMethod.selectedItemPosition]
                val notes  = dialogBinding.etNotes.text?.toString()?.trim()?.ifBlank { null }

                if (isEditing) {
                    viewModel.editPayment(
                        editPayment!!.id,
                        UpdatePaymentRequest(
                            amount   = amount,
                            methodId = method.id,
                            notes    = notes,
                        )
                    )
                } else {
                    val member = memberList[dialogBinding.spinnerMember.selectedItemPosition]
                    viewModel.addPayment(
                        CreatePaymentRequest(
                            memberId = member.id,
                            amount   = amount,
                            methodId = method.id,
                            notes    = notes,
                        )
                    )
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ── Delete confirmation ───────────────────────────────────────────────────

    private fun confirmDelete(payment: Payment) {
        val sym  = requireContext().gymApp.tokenManager.getCurrencySymbol()
        val name = payment.member?.fullName ?: "this member"
        val adj  = payment.walletAdjustment
        val adjMsg = when {
            adj > 0 -> "\n\nNote: ${adj.toCurrencyString(sym)} wallet credit will be reversed."
            adj < 0 -> "\n\nNote: ${(-adj).toCurrencyString(sym)} wallet debt will be cancelled."
            else    -> ""
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Payment")
            .setMessage(
                "Delete ${payment.amount.toCurrencyString(sym)} payment for $name " +
                "on ${payment.paymentDate.toDisplayDate()}?$adjMsg"
            )
            .setPositiveButton("Delete") { _, _ -> viewModel.deletePayment(payment.id) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
