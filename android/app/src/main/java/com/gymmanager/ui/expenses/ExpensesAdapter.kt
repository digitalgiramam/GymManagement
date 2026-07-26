package com.gymmanager.ui.expenses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Expense
import com.gymmanager.databinding.ItemExpenseBinding
import java.text.SimpleDateFormat
import java.util.Locale

class ExpensesAdapter(
    private val onDelete: (Expense) -> Unit,
) : ListAdapter<Expense, ExpensesAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            binding.tvExpenseTitle.text    = expense.title
            binding.tvExpenseCategory.text = expense.category?.name ?: "—"
            binding.tvExpenseAmount.text   = "₹%.2f".format(expense.amount)
            binding.tvExpenseDate.text     = formatDate(expense.expenseDate)
            binding.tvExpenseNotes.text    = expense.notes ?: ""
            binding.btnDeleteExpense.setOnClickListener { onDelete(expense) }
        }

        private fun formatDate(iso: String): String = try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val display = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            display.format(parser.parse(iso)!!)
        } catch (e: Exception) {
            iso.take(10)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Expense>() {
            override fun areItemsTheSame(a: Expense, b: Expense) = a.id == b.id
            override fun areContentsTheSame(a: Expense, b: Expense) = a == b
        }
    }
}
