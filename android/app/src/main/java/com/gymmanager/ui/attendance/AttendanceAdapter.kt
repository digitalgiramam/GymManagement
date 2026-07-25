package com.gymmanager.ui.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Attendance
import com.gymmanager.databinding.ItemAttendanceBinding
import com.gymmanager.utils.toDisplayDateTime

class AttendanceAdapter : ListAdapter<Attendance, AttendanceAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: Attendance) {
            b.tvMemberName.text = item.member?.fullName ?: "Member #${item.memberId}"
            b.tvPhone.text      = item.member?.phone ?: ""
            b.tvTime.text       = item.checkedInAt.toDisplayDateTime()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Attendance>() {
            override fun areItemsTheSame(a: Attendance, b: Attendance) = a.id == b.id
            override fun areContentsTheSame(a: Attendance, b: Attendance) = a == b
        }
    }
}
