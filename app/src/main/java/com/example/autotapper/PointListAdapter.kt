package com.example.autotapper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PointListAdapter(
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PointListAdapter.PointViewHolder>() {

    private var items: List<AutomationAction> = emptyList()

    fun submitPoints(actions: List<AutomationAction>) {
        items = actions
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
        val action = items[position]
        val context = holder.itemView.context
        holder.tvIndex.text = context.getString(R.string.point_item_index, position + 1)
        val delayText = if (action.delayMs > 0L) {
            context.getString(R.string.point_delay_value, action.delayMs)
        } else {
            context.getString(R.string.point_delay_global)
        }
        when (action) {
            is TapPoint -> {
                holder.tvCoords.text = context.getString(
                    R.string.point_item_coords,
                    action.x,
                    action.y
                )
                holder.tvDetails.text = context.getString(
                    R.string.point_detail_template,
                    delayText,
                    action.repeatCount,
                    action.holdDurationMs
                )
            }

            is GestureAction -> {
                val stroke = action.primaryStroke
                val start = stroke?.points?.firstOrNull()
                val end = stroke?.points?.lastOrNull()
                holder.tvCoords.text = if (start != null && end != null) {
                    context.getString(
                        R.string.gesture_item_path,
                        start.x,
                        start.y,
                        end.x,
                        end.y
                    )
                } else {
                    context.getString(R.string.gesture_item_unknown)
                }
                holder.tvDetails.text = context.getString(
                    R.string.gesture_detail_template,
                    delayText,
                    action.repeatCount,
                    stroke?.durationMs ?: GestureStroke.DEFAULT_DURATION_MS
                )
            }
        }
        holder.btnEdit.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) onEdit(currentPosition)
        }
        holder.itemView.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) onEdit(currentPosition)
        }
        holder.btnDelete.setOnClickListener {
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                onDelete(currentPosition)
            }
        }
    }

    class PointViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIndex: TextView = view.findViewById(R.id.tv_point_index)
        val tvCoords: TextView = view.findViewById(R.id.tv_point_coords)
        val tvDetails: TextView = view.findViewById(R.id.tv_point_details)
        val btnEdit: ImageView = view.findViewById(R.id.btn_point_edit)
        val btnDelete: ImageView = view.findViewById(R.id.btn_point_delete)
    }
}
