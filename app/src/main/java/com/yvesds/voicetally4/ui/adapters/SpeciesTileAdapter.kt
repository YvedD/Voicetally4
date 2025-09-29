package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.ui.data.SpeciesTile

class SpeciesTileAdapter(
    private var items: List<SpeciesTile>,
    private val onClick: (SpeciesTile) -> Unit
) : RecyclerView.Adapter<SpeciesTileAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: MaterialCardView = view.findViewById(R.id.tileRoot)
        val name: TextView = view.findViewById(R.id.tvSpeciesName)
        val count: TextView = view.findViewById(R.id.tvSpeciesCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_species_tile_vt4, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        // In SoortSelectie: canonical tonen
        holder.name.text = item.canonical
        // Max 2 regels en ellipsize zijn in XML gezet; hieronder enkel defensief
        holder.name.maxLines = 2

        // Count verbergen in selectie-scherm
        holder.count.visibility = View.GONE

        holder.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<SpeciesTile>) {
        items = newItems
        notifyDataSetChanged()
    }
}
