package com.gymmanager.ui.staff

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Member
import com.gymmanager.databinding.ItemStaffMemberBinding

class StaffMemberListAdapter :
    ListAdapter<Member, StaffMemberListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStaffMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(private val b: ItemStaffMemberBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(m: Member) {
            b.tvMemberName.text   = m.fullName
            b.tvMemberPhone.text  = m.phone
            b.tvMemberStatus.text = m.status
            b.tvMemberPlan.text   = m.plan?.name ?: ""
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Member>() {
            override fun areItemsTheSame(a: Member, b: Member) = a.id == b.id
            override fun areContentsTheSame(a: Member, b: Member) = a == b
        }
    }
}
