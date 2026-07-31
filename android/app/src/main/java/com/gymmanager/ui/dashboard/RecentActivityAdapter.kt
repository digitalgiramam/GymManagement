package com.gymmanager.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymmanager.R
import com.gymmanager.databinding.ItemRecentActivityBinding
import com.gymmanager.utils.toDisplayDateTime

/** Unified list item for the dashboard "Recent Activities" section. */
sealed class ActivityItem {
    data class CheckIn(
        val id: Int,
        val memberName: String,
        val time: String,
    ) : ActivityItem()
}

class RecentActivityAdapter :
    ListAdapter<ActivityItem, RecentActivityAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val b: ItemRecentActivityBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(item: ActivityItem) {
            when (item) {
                is ActivityItem.CheckIn -> {
                    b.ivIcon.setImageResource(R.drawable.ic_checkin)
                    b.tvTitle.text = item.memberName
                    b.tvSubtitle.text = b.root.context.getString(R.string.label_check_in)
                    b.tvDetail.text = item.time.toDisplayDateTime()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecentActivityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ActivityItem>() {
            override fun areItemsTheSame(a: ActivityItem, b: ActivityItem) = when {
                a is ActivityItem.CheckIn && b is ActivityItem.CheckIn -> a.id == b.id
                else -> false
            }
            override fun areContentsTheSame(a: ActivityItem, b: ActivityItem) = a == b
        }
    }
}
