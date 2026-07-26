package com.gymmanager.ui.expenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gymmanager.data.model.CreateExpenseRequest
import com.gymmanager.data.model.Expense
import com.gymmanager.data.model.ExpenseCategory
import com.gymmanager.databinding.DialogAddExpenseBinding
import com.gymmanager.databinding.FragmentExpensesBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.NetworkResult
import com.gymmanager.utils.hide
import com.gymmanager.utils.show
import com.gymmanager.utils.showSnackbarError

class ExpensesFragment : Fragment() {

    private var _binding: FragmentExpensesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExpensesViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ExpensesViewModel(requireContext().gymApp.expenseRepository) as T
            }
        }
    }

    private lateinit var adapter: ExpensesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExpensesAdapter(onDelete = { expense ->
            confirmDelete(expense)
        })
        binding.rvExpenses.adapter = adapter

        binding.fabAddExpense.setOnClickListener {
            showAddExpenseDialog()
        }

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadExpenses()
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadExpenses()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
        }

        viewModel.expenses.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            if (list.isEmpty()) binding.tvEmpty.show() else binding.tvEmpty.hide()
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) binding.root.showSnackbarError(msg)
        }

        viewModel.actionResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is NetworkResult.Error -> binding.root.showSnackbarError(result.message)
                else -> {}
            }
            if (result != null) viewModel.clearActionResult()
        }
    }

    private fun showAddExpenseDialog() {
        val categories = viewModel.categories.value ?: emptyList()
        if (categories.isEmpty()) {
            binding.root.showSnackbarError("No expense categories found")
            return
        }

        val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
        val categoryNames = categories.map { it.name }
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            categoryNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        dialogBinding.spinnerCategory.adapter = spinnerAdapter

        AlertDialog.Builder(requireContext())
            .setTitle("Add Expense")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val title      = dialogBinding.etExpenseTitle.text?.toString() ?: ""
                val amountStr  = dialogBinding.etExpenseAmount.text?.toString() ?: ""
                val notes      = dialogBinding.etExpenseNotes.text?.toString()
                val selectedIdx = dialogBinding.spinnerCategory.selectedItemPosition
                val category   = categories[selectedIdx]

                if (title.isBlank()) { binding.root.showSnackbarError("Title is required"); return@setPositiveButton }
                val amount = amountStr.toDoubleOrNull()
                if (amount == null || amount <= 0) { binding.root.showSnackbarError("Invalid amount"); return@setPositiveButton }

                viewModel.addExpense(
                    CreateExpenseRequest(
                        title      = title,
                        categoryId = category.id,
                        amount     = amount,
                        notes      = notes?.ifBlank { null },
                    )
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(expense: Expense) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Expense")
            .setMessage("Delete \"${expense.title}\"?")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteExpense(expense.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
