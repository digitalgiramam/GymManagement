package com.gymmanager.ui.attendance

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.data.model.Attendance
import com.gymmanager.databinding.ItemAttendanceBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AttendanceAdapter(
    private val onMarkOut: (Attendance) -> Unit = {},
) : ListAdapter<Attendance, AttendanceAdapter.ViewHolder>(DIFF) {

    private val inFmt  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        .also { it.timeZone = TimeZone.getTimeZone("UTC") }
    private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

    inner class ViewHolder(private val b: ItemAttendanceBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: Attendance) {
            b.tvMemberName.text = item.member?.fullName ?: "Member #${item.memberId}"
            b.tvPhone.text      = item.member?.phone ?: ""

            // Check-in time
            b.tvTimeIn.text = formatTime(item.checkedInAt)

            // Check-out time & duration
            val outTime = item.checkedOutAt
            if (outTime != null) {
                b.tvTimeOut.text = formatTime(outTime)
                b.tvDuration.text = duration(item.checkedInAt, outTime)
                b.tvDuration.visibility = android.view.View.VISIBLE
                b.btnMarkOut.visibility  = android.view.View.GONE   // already checked out
            } else {
                b.tvTimeOut.text = "—"
                b.tvDuration.visibility = android.view.View.GONE
                b.btnMarkOut.visibility  = android.view.View.VISIBLE
                b.btnMarkOut.setOnClickListener { onMarkOut(item) }
            }
        }

        private fun formatTime(iso: String): String = try {
            timeFmt.format(inFmt.parse(iso) ?: Date())
        } catch (_: Exception) {
            iso.take(5)
        }

        /** Returns "1h 23m" or "45m" string representing the session duration. */
        private fun duration(inIso: String, outIso: String): String = try {
            val inDate  = inFmt.parse(inIso) ?: return ""
            val outDate = inFmt.parse(outIso) ?: return ""
            val mins    = abs(outDate.time - inDate.time) / 60_000
            if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        } catch (_: Exception) { "" }
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
