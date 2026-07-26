package com.gymmanager.ui.payments

import android.os.Bundle
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.CreatePaymentRequest
import com.gymmanager.data.model.Member
import com.gymmanager.data.model.PaymentMethod
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

    private val adapter by lazy { PaymentsAdapter() }
    private var memberList: List<Member> = emptyList()
    private var methodList: List<PaymentMethod> = emptyList()

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
        binding.fabAddPayment.setOnClickListener { showAddPaymentDialog() }

        viewModel.members.observe(viewLifecycleOwner) { memberList = it }
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
                is NetworkResult.Success ->
                    binding.root.showSnackbar(getString(R.string.msg_payment_added))
                is NetworkResult.Error ->
                    binding.root.showSnackbarError(result.message)
                else -> Unit
            }
        }
    }

    private fun showAddPaymentDialog() {
        if (memberList.isEmpty()) {
            binding.root.showSnackbarError("No members found.")
            return
        }
        if (methodList.isEmpty()) {
            binding.root.showSnackbarError("No payment methods configured. Check Settings.")
            return
        }

        val dialogBinding = DialogAddPaymentBinding.inflate(layoutInflater)

        // Member spinner
        val memberNames = memberList.map { "${it.fullName} (${it.phone})" }
        dialogBinding.spinnerMember.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, memberNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Payment method spinner — now loaded from API (methodId FK)
        val methodNames = methodList.map { it.name }
        dialogBinding.spinnerMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, methodNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Update membership status card when member selection changes
        fun updateMembershipCard(member: Member) {
            dialogBinding.tvMemberJoinDate.text = "Joined: ${member.joinDate.toDisplayDate()}"
            dialogBinding.tvMemberLastPaid.text = if (member.lastPaymentDate != null)
                "Last paid: ${member.lastPaymentDate.toDisplayDate()}"
            else
                "Last paid: never"

            val days = member.daysUntilExpiry
            dialogBinding.tvMemberExpiry.text = when {
                days == null -> ""
                days < 0    -> "Membership expired ${-days} day(s) ago"
                days == 0   -> "Membership expires today"
                else        -> "Membership valid for $days more day(s)"
            }
            val expiryColor = when {
                days == null || days > 7 -> R.color.expiry_ok
                days in 1..7             -> R.color.expiry_warning
                else                     -> R.color.expiry_expired
            }
            dialogBinding.tvMemberExpiry.setTextColor(
                ContextCompat.getColor(requireContext(), expiryColor)
            )

            val plan = member.plan
            dialogBinding.tvMemberPlanFee.text = if (plan != null)
                "Plan: ${plan.name}  •  Fee: ${"%.2f".format(plan.fee)}  •  ${plan.durationDays} days"
            else ""
        }

        updateMembershipCard(memberList[0])

        dialogBinding.spinnerMember.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    updateMembershipCard(memberList[pos])
                    val fee = memberList[pos].plan?.fee
                    if (fee != null && dialogBinding.etAmount.text.isNullOrBlank()) {
                        dialogBinding.etAmount.setText("%.2f".format(fee))
                    }
                }
                override fun onNothingSelected(p: AdapterView<*>?) = Unit
            }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_add_payment)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val amountStr = dialogBinding.etAmount.text?.toString()?.trim() ?: ""
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    binding.root.showSnackbarError("Enter a valid amount.")
                    return@setPositiveButton
                }

                val member = memberList[dialogBinding.spinnerMember.selectedItemPosition]
                // Use methodId (FK) instead of method String
                val method = methodList[dialogBinding.spinnerMethod.selectedItemPosition]
                val notes  = dialogBinding.etNotes.text?.toString()?.trim()

                viewModel.addPayment(
                    CreatePaymentRequest(
                        memberId    = member.id,
                        amount      = amount,
                        methodId    = method.id,   // FK to PaymentMethod table
                        notes       = notes?.ifBlank { null },
                        paymentDate = null,        // defaults to now on backend
                    )
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPayments()
        viewModel.loadPaymentMethods()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
