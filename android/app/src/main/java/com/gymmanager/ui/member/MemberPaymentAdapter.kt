package com.gymmanager.ui.member

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Payment
import com.gymmanager.databinding.ItemMemberPaymentBinding
import com.gymmanager.gymApp
import com.gymmanager.utils.toCurrencyString
import java.text.SimpleDateFormat
import java.util.*

class MemberPaymentAdapter :
    ListAdapter<Payment, MemberPaymentAdapter.VH>(DIFF) {

    private val inFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    private val outFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMemberPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), inFmt, outFmt)

    class VH(private val b: ItemMemberPaymentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Payment, inFmt: SimpleDateFormat, outFmt: SimpleDateFormat) {
            val symbol = b.root.context.gymApp.tokenManager.getCurrencySymbol()
            b.tvPaymentAmount.text = p.amount.toCurrencyString(symbol)
            b.tvPaymentMethod.text = p.method?.name ?: "—"
            b.tvPaymentDate.text   = try {
                outFmt.format(inFmt.parse(p.paymentDate) ?: Date())
            } catch (_: Exception) { p.paymentDate }
            b.tvPaymentNotes.text  = p.notes ?: ""
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Payment>() {
            override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
            override fun areContentsTheSame(a: Payment, b: Payment) = a == b
        }
    }
}
