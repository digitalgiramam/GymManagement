package com.gymmanager.ui.payments

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gymmanager.R
import com.gymmanager.data.model.CreatePaymentRequest
import com.gymmanager.data.model.Member
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

    private val adapter = PaymentsAdapter()
    private var memberList: List<Member> = emptyList()
    private val methods = listOf("Cash", "Card", "Transfer")

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

        val dialogBinding = DialogAddPaymentBinding.inflate(layoutInflater)

        val memberNames = memberList.map { "${it.fullName} (${it.phone})" }
        dialogBinding.spinnerMember.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, memberNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        dialogBinding.spinnerMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, methods
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

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
                val method = methods[dialogBinding.spinnerMethod.selectedItemPosition]
                val notes  = dialogBinding.etNotes.text?.toString()?.trim()

                viewModel.addPayment(
                    CreatePaymentRequest(
                        memberId = member.id,
                        amount   = amount,
                        method   = method,
                        notes    = notes?.ifBlank { null },
                        paymentDate = null, // defaults to now
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
