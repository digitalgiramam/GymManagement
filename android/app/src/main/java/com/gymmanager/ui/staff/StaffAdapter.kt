package com.gymmanager.ui.staff

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Staff
import com.gymmanager.databinding.ItemStaffBinding

class StaffAdapter(
    private val onDelete: (Staff) -> Unit,
) : ListAdapter<Staff, StaffAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemStaffBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(staff: Staff) {
            binding.tvStaffName.text  = staff.fullName
            binding.tvStaffEmail.text = staff.email
            binding.tvStaffPhone.text = staff.phone ?: "—"
            binding.tvStaffRole.text  = staff.role.replace("_", " ")
            binding.btnDeleteStaff.setOnClickListener { onDelete(staff) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStaffBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Staff>() {
            override fun areItemsTheSame(a: Staff, b: Staff) = a.id == b.id
            override fun areContentsTheSame(a: Staff, b: Staff) = a == b
        }
    }
}
