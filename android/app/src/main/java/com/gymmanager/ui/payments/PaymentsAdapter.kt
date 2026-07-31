package com.gymmanager.ui.payments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemPaymentBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Locale

class PaymentsAdapter(
    private val onEdit: (Payment) -> Unit,
    private val onDelete: (Payment) -> Unit,
) : ListAdapter<Payment, PaymentsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemPaymentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: Payment) {
            val symbol = binding.root.context.gymApp.tokenManager.getCurrencySymbol()
            binding.tvPaymentMember.text  = payment.member?.fullName ?: "Member #${payment.memberId}"
            binding.tvPaymentAmount.text  = payment.amount.toCurrencyString(symbol)
            binding.tvPaymentMethod.text  = payment.method?.name ?: "—"
            binding.tvPaymentDate.text    = formatDate(payment.paymentDate)
            binding.tvPaymentExpiry.text  = payment.membershipExtendedTo
                ?.let { "Expires: ${formatDate(it)}" } ?: ""
            binding.tvPaymentNotes.text   = payment.notes ?: ""

            binding.btnEditPayment.setOnClickListener { onEdit(payment) }
            binding.btnDeletePayment.setOnClickListener { onDelete(payment) }
        }

        private fun formatDate(iso: String): String = try {
            val parser  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val parserZ = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val display = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = runCatching { parserZ.parse(iso)!! }.getOrNull()
                ?: parser.parse(iso)!!
            display.format(date)
        } catch (e: Exception) {
            iso.take(10)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Payment>() {
            override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
            override fun areContentsTheSame(a: Payment, b: Payment) = a == b
        }
    }
}
