package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.databinding.ItemAliasTileBinding
import com.yvesds.voicetally4.ui.data.SoortAlias

class AliasTileAdapter(
    private val isSelected: (String) -> Boolean,   // key = tileName
    private val onToggle: (String) -> Unit         // key = tileName
) : ListAdapter<SoortAlias, AliasTileAdapter.VH>(DIFF) {

    class VH(val binding: ItemAliasTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAliasTileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            /* attachToRoot = */ false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val btn: MaterialButton = holder.binding.btnTile
        // TEKST OP TILE = kolom 2 (tileName)
        btn.text = item.tileName
        btn.isChecked = isSelected(item.tileName)
        btn.setOnClickListener {
            onToggle(item.tileName)
            btn.isChecked = isSelected(item.tileName)
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).tileName.hashCode().toLong()

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<SoortAlias>() {
            override fun areItemsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem.tileName == newItem.tileName
            override fun areContentsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem == newItem
        }
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }
}
