package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally4.databinding.ItemTallyBinding
import com.yvesds.voicetally4.ui.tally.TallyViewModel

/**
 * Toont displayName (tile-name) i.p.v. canonical, met +/-/reset knoppen.
 */
class TallyAdapter(
    private val onIncrement: (canonical: String) -> Unit,
    private val onDecrement: (canonical: String) -> Unit,
    private val onReset: (canonical: String) -> Unit
) : ListAdapter<TallyViewModel.TallyItem, TallyAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TallyViewModel.TallyItem>() {
            override fun areItemsTheSame(oldItem: TallyViewModel.TallyItem, newItem: TallyViewModel.TallyItem): Boolean =
                oldItem.canonical == newItem.canonical

            override fun areContentsTheSame(oldItem: TallyViewModel.TallyItem, newItem: TallyViewModel.TallyItem): Boolean =
                oldItem == newItem
        }
    }

    class VH(val binding: ItemTallyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTallyBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            textSpeciesName.text = item.displayName
            textCount.text = item.count.toString()

            buttonIncrement.setOnClickListener { onIncrement(item.canonical) }
            buttonDecrement.setOnClickListener { onDecrement(item.canonical) }
            buttonReset.setOnClickListener     { onReset(item.canonical) }
        }
    }
}
