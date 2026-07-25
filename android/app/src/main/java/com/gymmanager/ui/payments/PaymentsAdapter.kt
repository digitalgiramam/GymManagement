package com.gymmanager.ui.payments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemPaymentBinding
import com.gymmanager.utils.toCurrencyString
import com.gymmanager.utils.toDisplayDate

class PaymentsAdapter : ListAdapter<Payment, PaymentsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemPaymentBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: Payment) {
            b.tvMemberName.text = item.member?.fullName ?: "Member #${item.memberId}"
            b.tvAmount.text     = item.amount.toCurrencyString()
            b.tvMethod.text     = item.method
            b.tvDate.text       = item.paymentDate.toDisplayDate()
            b.tvNotes.text      = item.notes ?: ""
            b.tvNotes.visibility = if (item.notes.isNullOrBlank()) ViewGroup.GONE else ViewGroup.VISIBLE
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
