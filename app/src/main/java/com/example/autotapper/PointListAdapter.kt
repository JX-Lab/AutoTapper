package com.example.autotapper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PointListAdapter(
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PointListAdapter.PointViewHolder>() {

    private var items: List<TapPoint> = emptyList()

    fun submitPoints(points: List<TapPoint>) {
        items = points
        notifyDataSetChanged()
    }

    fun removeItem(index: Int) {
        if (index !in items.indices) {
            return
        }
        items = items.toMutableList().apply { removeAt(index) }
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, items.size - index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PointViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_point, parent, false)
        return PointViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PointViewHolder, position: Int) {
        val point = items[position]
        val context = holder.itemView.context
        holder.tvIndex.text = context.getString(R.string.point_item_index, position + 1)
        holder.tvCoords.text = context.getString(R.string.point_item_coords, point.x, point.y)
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    class PointViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tv_point_index)
        val tvCoords: TextView = view.findViewById(R.id.tv_point_coords)
        val btnDelete: ImageView = view.findViewById(R.id.btn_point_delete)
    }
}
