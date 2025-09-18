package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.R

/**
 * Lichte adapter voor actieve soorten met knoppen – / + / reset.
 * Layout: item_tally.xml (door jou te plakken).
 */
class TallyAdapter(
    private val onIncrement: (speciesId: String) -> Unit,
    private val onDecrement: (speciesId: String) -> Unit,
    private val onReset: (speciesId: String) -> Unit
) : ListAdapter<TallyItem, TallyVH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TallyVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_tally, parent, false)
        return TallyVH(v as ViewGroup, onIncrement, onDecrement, onReset)
    }

    override fun onBindViewHolder(holder: TallyVH, position: Int) {
        holder.bind(getItem(position))
    }

    object Diff : DiffUtil.ItemCallback<TallyItem>() {
        override fun areItemsTheSame(oldItem: TallyItem, newItem: TallyItem): Boolean =
            oldItem.speciesId == newItem.speciesId

        override fun areContentsTheSame(oldItem: TallyItem, newItem: TallyItem): Boolean =
            oldItem == newItem
    }
}

data class TallyItem(
    val speciesId: String,
    val name: String,
    val count: Int
)

class TallyVH(
    private val root: ViewGroup,
    private val onIncrement: (String) -> Unit,
    private val onDecrement: (String) -> Unit,
    private val onReset: (String) -> Unit
) : RecyclerView.ViewHolder(root) {

    private val tvName: TextView = root.findViewById(R.id.textSpeciesName)
    private val tvCount: TextView = root.findViewById(R.id.textCount)
    private val btnPlus: MaterialButton = root.findViewById(R.id.buttonIncrement)
    private val btnMinus: MaterialButton = root.findViewById(R.id.buttonDecrement)
    private val btnReset: MaterialButton = root.findViewById(R.id.buttonReset)

    fun bind(item: TallyItem) {
        tvName.text = item.name
        tvCount.text = item.count.toString()
        btnPlus.setOnClickListener { onIncrement(item.speciesId) }
        btnMinus.setOnClickListener { onDecrement(item.speciesId) }
        btnReset.setOnClickListener { onReset(item.speciesId) }
    }
}
