package com.gymmanager.ui.payments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.R
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemPaymentBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentsAdapter(
    private val onEdit: (Payment) -> Unit,
    private val onDelete: (Payment) -> Unit,
) : ListAdapter<Payment, PaymentsAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemPaymentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: Payment) {
            val ctx    = binding.root.context
            val symbol = ctx.gymApp.tokenManager.getCurrencySymbol()

            // ── Member name ────────────────────────────────────────────────────
            binding.tvPaymentMember.text =
                payment.member?.fullName ?: "Member #${payment.memberId}"

            // ── Status badge ───────────────────────────────────────────────────
            // Compute locally from expiry + planFee so it's correct even when
            // the backend hasn't been redeployed yet.
            val status = computeStatus(
                isoDate  = payment.membershipExtendedTo,
                planFee  = payment.planFee,
                amount   = payment.amount,
            ) ?: payment.membershipStatus ?: "Overdue"

            binding.tvPaymentStatus.text = status
            binding.tvPaymentStatus.setBackgroundResource(
                when (status) {
                    "Active"  -> R.drawable.bg_status_active
                    "Partial" -> R.drawable.bg_status_partial
                    else      -> R.drawable.bg_status_overdue   // "Overdue"
                }
            )

            // ── Subscription plan name ─────────────────────────────────────────
            val planName = payment.planName
            if (planName != null) {
                binding.tvPaymentPlan.visibility = View.VISIBLE
                val feeStr = payment.planFee?.let { " · ${it.toCurrencyString(symbol)}" } ?: ""
                binding.tvPaymentPlan.text =
                    "Subscription: $planName (${payment.planDurationDays} days$feeStr)"
            } else {
                binding.tvPaymentPlan.visibility = View.GONE
            }

            // ── Payment method · date ──────────────────────────────────────────
            binding.tvPaymentMethod.text = payment.method?.name ?: "—"
            binding.tvPaymentDate.text   = formatDate(payment.paymentDate)

            // ── Expiry ─────────────────────────────────────────────────────────
            val expiry = payment.membershipExtendedTo
            if (expiry != null) {
                binding.tvPaymentExpiry.visibility = View.VISIBLE
                binding.tvPaymentExpiry.text = "Valid until: ${formatDate(expiry)}"
                binding.tvPaymentExpiry.setTextColor(
                    if (status == "Overdue") android.graphics.Color.parseColor("#C62828")
                    else android.graphics.Color.parseColor("#2E7D32")
                )
            } else {
                binding.tvPaymentExpiry.visibility = View.GONE
            }

            // ── Balance due row (Partial only) ─────────────────────────────────
            val overdueAmt = payment.overdueAmount
                ?: run {
                    val fee = payment.planFee ?: 0.0
                    if (fee > payment.amount) fee - payment.amount else 0.0
                }
            if (status == "Partial" && overdueAmt > 0) {
                binding.rowBalanceDue.visibility = View.VISIBLE
                binding.tvBalanceDue.text = overdueAmt.toCurrencyString(symbol)
            } else {
                binding.rowBalanceDue.visibility = View.GONE
            }

            // ── Notes ──────────────────────────────────────────────────────────
            val notes = payment.notes
            if (!notes.isNullOrBlank()) {
                binding.tvPaymentNotes.visibility = View.VISIBLE
                binding.tvPaymentNotes.text = notes
            } else {
                binding.tvPaymentNotes.visibility = View.GONE
            }

            // ── Amount paid (right column) ─────────────────────────────────────
            binding.tvPaymentAmount.text = payment.amount.toCurrencyString(symbol)

            binding.btnEditPayment.setOnClickListener { onEdit(payment) }
            binding.btnDeletePayment.setOnClickListener { onDelete(payment) }
        }

        /**
         * Computes status from the payment's own data:
         *  - "Overdue"  — subscription period has lapsed
         *  - "Partial"  — subscription active, but paidAmount < planFee
         *  - "Active"   — subscription active and fully paid
         * Returns null if [isoDate] is null or unparseable.
         */
        private fun computeStatus(isoDate: String?, planFee: Double?, amount: Double): String? {
            isoDate ?: return null
            return try {
                val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val expiry = runCatching { parserZ.parse(isoDate)!! }.getOrNull()
                    ?: parserMs.parse(isoDate) ?: return null
                if (!expiry.after(Date())) return "Overdue"
                // Subscription still active — check if fully paid
                val fee = planFee ?: 0.0
                if (fee > 0 && amount < fee) "Partial" else "Active"
            } catch (e: Exception) { null }
        }

        private fun formatDate(iso: String): String = try {
            val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val display  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = runCatching { parserZ.parse(iso)!! }.getOrNull()
                ?: parserMs.parse(iso)!!
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
