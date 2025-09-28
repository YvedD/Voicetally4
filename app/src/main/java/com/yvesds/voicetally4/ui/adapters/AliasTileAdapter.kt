package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.databinding.ItemAliasTileBinding
import com.yvesds.voicetally4.ui.data.SoortAlias

/**
 * Snelle, GC-arme tile-adapter:
 * - ListAdapter + DiffUtil met stableIds
 * - Reused click-listener per ViewHolder (geen alloc per bind)
 * - Payloads voor snelle (de)selectie zonder volledige herbind
 */
class AliasTileAdapter(
    private val isSelected: (String) -> Boolean, // key = tileName
    private val onToggle: (String) -> Unit       // key = tileName
) : ListAdapter<SoortAlias, AliasTileAdapter.VH>(DIFF) {

    companion object {
        /** Gebruik een object i.p.v. Int om boxing/equals-kosten te vermijden en === te kunnen gebruiken. */
        private object PayloadSelection

        private val DIFF = object : DiffUtil.ItemCallback<SoortAlias>() {
            override fun areItemsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem.tileName == newItem.tileName

            override fun areContentsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem == newItem
        }

        /**
         * Stabiele 64-bit hash (FNV-1a) voor minder kans op botsingen dan String.hashCode().
         * Let op: we gebruiken de gesigneerde representatie van de 64-bit offset/prime.
         */
        private fun stableIdFromKey(key: String): Long {
            var hash = -3750763034362895579L          // 0xcbf29ce484222325 als signed long
            val prime = 1099511628211L               // 0x00000100000001B3
            for (ch in key) {
                hash = hash xor ch.code.toLong()
                hash *= prime
            }
            return hash
        }
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    inner class VH(val binding: ItemAliasTileBinding) : RecyclerView.ViewHolder(binding.root) {
        private val btn: MaterialButton = binding.btnTile

        init {
            // Eén listener per ViewHolder; we lezen item via bindingAdapterPosition
            btn.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = getItem(pos)
                onToggle(item.tileName)
                // Vraag snelle rebind van enkel de selectie-state
                this@AliasTileAdapter.notifyItemChanged(pos, PayloadSelection)
            }
        }

        fun bindFull(item: SoortAlias) = with(btn) {
            // Tekst op tile = kolom 2 (tileName)
            text = item.tileName
            isChecked = isSelected(item.tileName)
        }

        fun bindSelectionOnly(item: SoortAlias) {
            btn.isChecked = isSelected(item.tileName)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAliasTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, /* attachToRoot = */ false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bindFull(getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] === PayloadSelection) {
            holder.bindSelectionOnly(getItem(position))
        } else {
            holder.bindFull(getItem(position))
        }
    }

    override fun getItemId(position: Int): Long =
        stableIdFromKey(getItem(position).tileName)
}
