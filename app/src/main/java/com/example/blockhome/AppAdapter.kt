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
    private val onClick: (AppEntry) -> Unit,
    private val currentTheme: () -> String
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
        val palette = when (currentTheme()) {
            "Stone" -> 0xFF777777.toInt() to 0xFFCCCCCC.toInt()
            "Diamond" -> 0xFF087D91.toInt() to 0xFF7DF4FF.toInt()
            "Amethyst" -> 0xFF58336F.toInt() to 0xFFE0B0FF.toInt()
            "Glass" -> 0x5578C7E8 to 0x99E5FAFF.toInt()
            "Nether" -> 0xFF5A1515.toInt() to 0xFFFF8060.toInt()
            else -> 0xFF31551E.toInt() to 0xFF9BE36C.toInt()
        }
        holder.icon.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(palette.first)
            setStroke((2 * holder.itemView.resources.displayMetrics.density).toInt().coerceAtLeast(1), palette.second)
        }
        holder.icon.setPadding(3, 3, 3, 3)
        holder.label.text = entry.label
    }
}
