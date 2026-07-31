package com.gymmanager.ui.payments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemPaymentBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.toCurrencyString
import com.gymmanager.utils.toDisplayDate

class PaymentsAdapter(
    private val onEdit:   (Payment) -> Unit = {},
    private val onDelete: (Payment) -> Unit = {},
) : ListAdapter<Payment, PaymentsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemPaymentBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: Payment) {
            val sym = b.root.context.gymApp.tokenManager.getCurrencySymbol()

            b.tvMemberName.text = item.member?.fullName ?: "Member #${item.memberId}"
            b.tvAmount.text     = item.amount.toCurrencyString(sym)
            b.tvMethod.text     = item.method?.name ?: "—"
            b.tvDate.text       = item.paymentDate.toDisplayDate()

            // Notes
            if (item.notes.isNullOrBlank()) {
                b.tvNotes.visibility = View.GONE
            } else {
                b.tvNotes.text       = item.notes
                b.tvNotes.visibility = View.VISIBLE
            }

            // Wallet adjustment
            val adj = item.walletAdjustment
            if (adj != 0.0) {
                val ctx = b.root.context
                when {
                    adj > 0 -> {
                        b.tvWalletAdjustment.text = "+${adj.toCurrencyString(sym)} credited to wallet"
                        b.tvWalletAdjustment.setTextColor(ctx.getColor(com.gymmanager.R.color.success))
                    }
                    else -> {
                        b.tvWalletAdjustment.text = "${adj.toCurrencyString(sym)} debited from wallet"
                        b.tvWalletAdjustment.setTextColor(ctx.getColor(com.gymmanager.R.color.expiry_expired))
                    }
                }
                b.tvWalletAdjustment.visibility = View.VISIBLE
            } else {
                b.tvWalletAdjustment.visibility = View.GONE
            }

            b.btnEditPayment.setOnClickListener   { onEdit(item) }
            b.btnDeletePayment.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Payment>() {
            override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
            override fun areContentsTheSame(a: Payment, b: Payment) = a == b
        }
    }
}
