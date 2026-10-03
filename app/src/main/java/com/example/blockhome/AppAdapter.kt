package com.example.blockhome

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

private object AppDiff : DiffUtil.ItemCallback<AppEntry>() {
    override fun areItemsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
        oldItem.component == newItem.component && oldItem.user == newItem.user

    override fun areContentsTheSame(oldItem: AppEntry, newItem: AppEntry): Boolean =
        oldItem == newItem
}

class AppAdapter(
    private val onClick: (AppEntry) -> Unit
) : ListAdapter<AppEntry, AppAdapter.VH>(AppDiff) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        val holder = VH(view)
        view.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
        }
        return holder
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        holder.icon.setImageDrawable(entry.icon)
        holder.label.text = entry.label
    }
}
