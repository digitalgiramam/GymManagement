package com.gymmanager.ui.staff

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Attendance
import com.gymmanager.databinding.ItemStaffAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class StaffAttendanceAdapter :
    ListAdapter<Attendance, StaffAttendanceAdapter.VH>(DIFF) {

    private val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemStaffAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), fmt)

    class VH(private val b: ItemStaffAttendanceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(a: Attendance, fmt: SimpleDateFormat) {
            b.tvAttendanceName.text = a.member?.fullName ?: "Member #${a.memberId}"
            try {
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                    .also { it.timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(a.checkedInAt)
                b.tvAttendanceTime.text = fmt.format(date ?: Date())
            } catch (_: Exception) {
                b.tvAttendanceTime.text = a.checkedInAt
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Attendance>() {
            override fun areItemsTheSame(a: Attendance, b: Attendance) = a.id == b.id
            override fun areContentsTheSame(a: Attendance, b: Attendance) = a == b
        }
    }
}
