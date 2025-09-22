package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally4.R

/**
 * VT3-achtige tegelweergave: per soort één compacte "tile"
 * met duidelijk zichtbare weergavenaam (aliasmapping kolom 2) en huidig aantal.
 *
 * - Klik op de tile: open de aanpas-popup (geregeld door caller via onItemClick).
 * - displayNameOf: lambda die de juiste weergavenaam levert op basis van canonieke soort.
 */
class SpeciesTileAdapter(
    private val onItemClick: (speciesCanonical: String) -> Unit,
    private val displayNameOf: (speciesCanonical: String) -> String = { it }
) : RecyclerView.Adapter<SpeciesTileAdapter.VH>() {

    data class Tile(val speciesCanonical: String, val count: Int)

    private val items = ArrayList<Tile>()

    fun submit(newItems: List<Tile>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                items[oldItemPosition].speciesCanonical == newItems[newItemPosition].speciesCanonical
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                items[oldItemPosition] == newItems[newItemPosition]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_species_tile_vt4, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = displayNameOf(item.speciesCanonical) // <-- weergavenaam
        holder.tvCount.text = item.count.toString()

        holder.itemView.setOnClickListener {
            onItemClick.invoke(item.speciesCanonical)
        }
    }

    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvSpeciesName)
        val tvCount: TextView = view.findViewById(R.id.tvSpeciesCount)
    }
}
