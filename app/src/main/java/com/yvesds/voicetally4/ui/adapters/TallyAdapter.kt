package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.R

/**
 * Back-compat adapter voor een "tellerlijst".
 * - Houdt een eigen Item type aan (speciesName + count) zodat er geen afhankelijkheid
 *   meer is van TallyScherm.TallyItem of andere externe types.
 * - Gebruikt item_tally.xml, maar verbergt de inline ±/reset knoppen (popup-UX).
 * - item click -> callback (bijv. om SpeciesAdjustDialog te tonen).
 *
 * Kan veilig blijven staan, ook als je elders SpeciesTileAdapter gebruikt.
 */
class TallyAdapter(
    private val onItemClick: (speciesName: String) -> Unit
) : RecyclerView.Adapter<TallyAdapter.VH>() {

    data class Item(val speciesName: String, val count: Int)

    private val items = ArrayList<Item>()

    fun submit(newItems: List<Item>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].speciesName == newItems[newItemPosition].speciesName
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tally, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.textSpeciesName.text = item.speciesName
        holder.textCount.text = item.count.toString()

        // Inline knoppen verbergen: we werken met popup-UX (VT3-stijl)
        holder.buttonInc.visibility = View.GONE
        holder.buttonDec.visibility = View.GONE
        holder.buttonReset.visibility = View.GONE

        holder.itemView.setOnClickListener {
            onItemClick.invoke(item.speciesName)
        }
    }

    override fun getItemCount(): Int = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val textSpeciesName: TextView = view.findViewById(R.id.textSpeciesName)
        val textCount: TextView = view.findViewById(R.id.textCount)
        val buttonInc: MaterialButton = view.findViewById(R.id.buttonIncrement)
        val buttonDec: MaterialButton = view.findViewById(R.id.buttonDecrement)
        val buttonReset: MaterialButton = view.findViewById(R.id.buttonReset)
    }
}
