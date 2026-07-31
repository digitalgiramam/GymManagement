package com.gymmanager.ui.member

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.R
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemMemberPaymentBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.toCurrencyString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemberPaymentAdapter :
    ListAdapter<Payment, MemberPaymentAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemMemberPaymentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(payment: Payment) {
            val symbol = binding.root.context.gymApp.tokenManager.getCurrencySymbol()

            // Plan name
            binding.tvMpPlanName.text =
                payment.planName ?: "Subscription (${payment.planDurationDays} days)"

            // Payment method + date
            binding.tvMpMethod.text = payment.method?.name ?: "—"
            binding.tvMpDate.text   = formatDate(payment.paymentDate)

            // Expiry
            val expiry = payment.membershipExtendedTo
            if (expiry != null) {
                binding.tvMpExpiry.visibility = View.VISIBLE
                binding.tvMpExpiry.text = "Valid until: ${formatDate(expiry)}"
            } else {
                binding.tvMpExpiry.visibility = View.GONE
            }

            // Compute status
            val status = computeStatus(expiry, payment.planFee, payment.amount)
                ?: payment.membershipStatus
                ?: "Not Paid"

            // Status badge
            binding.tvMpStatus.text = status
            binding.tvMpStatus.setBackgroundResource(
                when (status) {
                    "Full Paid"    -> R.drawable.bg_status_active
                    "Partial Paid" -> R.drawable.bg_status_partial
                    else           -> R.drawable.bg_status_overdue  // "Not Paid"
                }
            )
            // Expiry text color
            if (expiry != null) {
                binding.tvMpExpiry.setTextColor(
                    if (status == "Not Paid") android.graphics.Color.parseColor("#C62828")
                    else android.graphics.Color.parseColor("#2E7D32")
                )
            }

            // Balance due (Partial Paid only)
            val overdueAmt = payment.overdueAmount
                ?: ((payment.planFee ?: 0.0) - payment.amount).coerceAtLeast(0.0)
            if (status == "Partial Paid" && overdueAmt > 0) {
                binding.tvMpBalance.visibility = View.VISIBLE
                binding.tvMpBalance.text = "Balance due: ${overdueAmt.toCurrencyString(symbol)}"
            } else {
                binding.tvMpBalance.visibility = View.GONE
            }

            // Amount paid
            binding.tvMpAmount.text = payment.amount.toCurrencyString(symbol)
        }

        private fun computeStatus(isoDate: String?, planFee: Double?, amount: Double): String? {
            isoDate ?: return null
            return try {
                val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val expiry   = runCatching { parserZ.parse(isoDate)!! }.getOrNull()
                    ?: parserMs.parse(isoDate) ?: return null
                if (!expiry.after(Date())) return "Not Paid"
                val fee = planFee ?: 0.0
                if (fee > 0 && amount < fee) "Partial Paid" else "Full Paid"
            } catch (e: Exception) { null }
        }

        private fun formatDate(iso: String): String = try {
            val parserZ  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val parserMs = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val display  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val date = runCatching { parserZ.parse(iso)!! }.getOrNull() ?: parserMs.parse(iso)!!
            display.format(date)
        } catch (e: Exception) { iso.take(10) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMemberPaymentBinding.inflate(
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
