package com.example.autotapper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProfileListAdapter(
    private val onLoad: (AutomationProfile) -> Unit,
    private val onEdit: (AutomationProfile) -> Unit,
    private val onDelete: (AutomationProfile) -> Unit
) : RecyclerView.Adapter<ProfileListAdapter.ProfileViewHolder>() {

    private var items: List<AutomationProfile> = emptyList()
    private var activeId: String? = null

    fun submitProfiles(profiles: List<AutomationProfile>, activeProfileId: String?) {
        items = profiles
        activeId = activeProfileId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = items[position]
        val context = holder.itemView.context
        holder.name.text = profile.name
        holder.summary.text = context.getString(
            R.string.profile_summary_template,
            profile.actions.size,
            profile.intervalMs,
            if (profile.repeatCount == 0) context.getString(R.string.overlay_repeat_infinite)
            else context.getString(R.string.overlay_repeat_finite, profile.repeatCount)
        )
        holder.active.visibility = if (profile.id == activeId) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onLoad(profile) }
        holder.edit.setOnClickListener { onEdit(profile) }
        holder.delete.setOnClickListener { onDelete(profile) }
    }

    class ProfileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_profile_item_name)
        val summary: TextView = view.findViewById(R.id.tv_profile_item_summary)
        val active: TextView = view.findViewById(R.id.tv_profile_item_active)
        val edit: ImageView = view.findViewById(R.id.btn_profile_item_edit)
        val delete: ImageView = view.findViewById(R.id.btn_profile_item_delete)
    }
}
