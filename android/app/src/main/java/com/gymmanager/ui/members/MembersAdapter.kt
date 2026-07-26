package com.gymmanager.ui.members

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.R
import com.gymmanager.data.model.Member
import com.gymmanager.databinding.ItemMemberBinding
import com.gymmanager.utils.toDisplayDate

class MembersAdapter(
    private val onItemClick: (Member) -> Unit,
    private val onItemLongClick: (Member) -> Unit,
) : ListAdapter<Member, MembersAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemMemberBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(member: Member) {
            val ctx = b.root.context

            b.tvName.text   = member.fullName
            b.tvPhone.text  = member.phone
            b.tvPlan.text   = member.plan?.name ?: "—"
            b.tvStatus.text = member.status

            val statusColor = if (member.status == "Active")
                R.color.status_active else R.color.status_inactive
            b.tvStatus.setTextColor(ContextCompat.getColor(ctx, statusColor))

            // Last paid
            b.tvLastPaid.text = if (member.lastPaymentDate != null)
                "Last paid: ${member.lastPaymentDate.toDisplayDate()}"
            else
                "Last paid: never"

            // Membership expiry with colour coding
            val days = member.daysUntilExpiry
            val expiryText = when {
                days == null                   -> ""
                days < 0                       -> "Expired ${-days}d ago"
                days == 0                      -> "Expires today"
                else                           -> "Expires in ${days}d"
            }
            val expiryColor = when {
                days == null || days > 7       -> R.color.expiry_ok
                days in 1..7                   -> R.color.expiry_warning
                else                           -> R.color.expiry_expired   // 0 or negative
            }
            b.tvExpiry.text = expiryText
            b.tvExpiry.setTextColor(ContextCompat.getColor(ctx, expiryColor))

            b.root.setOnClickListener     { onItemClick(member) }
            b.root.setOnLongClickListener { onItemLongClick(member); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Member>() {
            override fun areItemsTheSame(a: Member, b: Member) = a.id == b.id
            override fun areContentsTheSame(a: Member, b: Member) = a == b
        }
    }
}
