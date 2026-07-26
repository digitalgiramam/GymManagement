package com.gymmanager.ui.member

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Attendance
import com.gymmanager.databinding.ItemMemberAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*

class MemberAttendanceAdapter :
    ListAdapter<Attendance, MemberAttendanceAdapter.VH>(DIFF) {

    private val inFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).also { it.timeZone = TimeZone.getTimeZone("UTC") }
    private val outFmt = SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMemberAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position), inFmt, outFmt)

    class VH(private val b: ItemMemberAttendanceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(a: Attendance, inFmt: SimpleDateFormat, outFmt: SimpleDateFormat) {
            b.tvCheckedInAt.text = try {
                outFmt.format(inFmt.parse(a.checkedInAt) ?: Date())
            } catch (_: Exception) { a.checkedInAt }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Attendance>() {
            override fun areItemsTheSame(a: Attendance, b: Attendance) = a.id == b.id
            override fun areContentsTheSame(a: Attendance, b: Attendance) = a == b
        }
    }
}
