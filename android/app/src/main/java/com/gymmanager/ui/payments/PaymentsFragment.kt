package com.gymmanager.ui.payments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.data.model.*
import com.gymmanager.data.repository.MemberRepository
import com.gymmanager.databinding.DialogAddPaymentBinding
import com.gymmanager.databinding.DialogEditPaymentBinding
import com.gymmanager.databinding.FragmentPaymentsBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbar
import com.gymmanager.utils.showSnackbarError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaymentsViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PaymentsViewModel(requireContext().gymApp.paymentRepository) as T
            }
        })[PaymentsViewModel::class.java]
    }

    private lateinit var adapter: PaymentsAdapter
    private var allMembers: List<Member> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Prefetch members list for the add-payment dialog spinner
        loadMembers()

        adapter = PaymentsAdapter(
            onEdit   = { payment -> showEditPaymentDialog(payment) },
            onDelete = { payment -> confirmDelete(payment) },
        )
        binding.rvPayments.adapter = adapter

        binding.fabAddPayment.setOnClickListener { showAddPaymentDialog() }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadPayments()
            viewModel.loadExpiringMembers()
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPayments()
        viewModel.loadExpiringMembers()
    }

    private fun loadMembers() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = requireContext().gymApp.memberRepository.getMembers()
            withContext(Dispatchers.Main) {
                if (result is NetworkResult.Success) allMembers = result.data
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.payments.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) binding.tvEmpty.show() else binding.tvEmpty.hide()
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) binding.root.showSnackbarError(msg)
        }

        viewModel.expiringMembers.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                binding.bannerExpiring.hide()
            } else {
                binding.bannerExpiring.show()
                binding.tvExpiringCount.text =
                    "${list.size} member${if (list.size > 1) "s" else ""} expiring within 30 days"
            }
        }

        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Success -> binding.root.showSnackbar("Done")
                is NetworkResult.Error   -> binding.root.showSnackbarError(result.message)
                else -> {}
            }
            if (result != null) viewModel.clearActionResult()
        }
    }

    // ── Add Payment Dialog ─────────────────────────────────────────────────────

    private fun showAddPaymentDialog() {
        val methods = viewModel.paymentMethods.value ?: emptyList()
        val members = allMembers

        if (members.isEmpty()) {
            binding.root.showSnackbarError("No members found. Add members first.")
            return
        }
        if (methods.isEmpty()) {
            binding.root.showSnackbarError("No payment methods found. Contact admin.")
            return
        }

        val dialogBinding = DialogAddPaymentBinding.inflate(layoutInflater)

        // Member spinner
        val memberNames = members.map { it.fullName }
        dialogBinding.spinnerMember.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, memberNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Method spinner
        val methodNames = methods.map { it.name }
        dialogBinding.spinnerPaymentMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, methodNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(requireContext())
            .setTitle("Record Payment")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val memberIdx = dialogBinding.spinnerMember.selectedItemPosition
                val methodIdx = dialogBinding.spinnerPaymentMethod.selectedItemPosition
                val amountStr = dialogBinding.etPaymentAmount.text?.toString() ?: ""
                val notes     = dialogBinding.etPaymentNotes.text?.toString()

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    binding.root.showSnackbarError("Enter a valid amount")
                    return@setPositiveButton
                }

                viewModel.createPayment(
                    CreatePaymentRequest(
                        memberId = members[memberIdx].id,
                        amount   = amount,
                        methodId = methods[methodIdx].id,
                        notes    = notes?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Edit Payment Dialog ────────────────────────────────────────────────────

    private fun showEditPaymentDialog(payment: Payment) {
        val methods = viewModel.paymentMethods.value ?: emptyList()
        if (methods.isEmpty()) {
            binding.root.showSnackbarError("No payment methods available")
            return
        }

        val dialogBinding = DialogEditPaymentBinding.inflate(layoutInflater)

        // Pre-fill
        dialogBinding.etEditPaymentAmount.setText(payment.amount.toString())
        dialogBinding.etEditPaymentNotes.setText(payment.notes ?: "")

        val methodNames = methods.map { it.name }
        val currentMethodIdx = methods.indexOfFirst { it.id == payment.methodId }.coerceAtLeast(0)
        dialogBinding.spinnerEditPaymentMethod.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, methodNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        dialogBinding.spinnerEditPaymentMethod.setSelection(currentMethodIdx)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Payment")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val amountStr = dialogBinding.etEditPaymentAmount.text?.toString() ?: ""
                val notes     = dialogBinding.etEditPaymentNotes.text?.toString()
                val methodIdx = dialogBinding.spinnerEditPaymentMethod.selectedItemPosition

                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    binding.root.showSnackbarError("Enter a valid amount")
                    return@setPositiveButton
                }

                viewModel.updatePayment(
                    payment.id,
                    UpdatePaymentRequest(
                        amount   = amount,
                        methodId = methods[methodIdx].id,
                        notes    = notes?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Delete Confirmation ────────────────────────────────────────────────────

    private fun confirmDelete(payment: Payment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Payment")
            .setMessage("Delete payment of ${payment.amount} for ${payment.member?.fullName ?: "this member"}?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deletePayment(payment.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
