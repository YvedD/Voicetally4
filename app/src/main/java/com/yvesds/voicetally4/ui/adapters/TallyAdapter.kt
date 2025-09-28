package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally4.databinding.ItemSpeciesTileVt4Binding

class TallyAdapter(
    private val onTileClick: (speciesId: String) -> Unit
) : ListAdapter<TallyItem, TallyAdapter.VH>(DIFF) {

    companion object {
        private const val PAYLOAD_COUNT = 1

        val DIFF = object : DiffUtil.ItemCallback<TallyItem>() {
            override fun areItemsTheSame(oldItem: TallyItem, newItem: TallyItem): Boolean =
                oldItem.speciesId == newItem.speciesId

            override fun areContentsTheSame(oldItem: TallyItem, newItem: TallyItem): Boolean =
                oldItem.name == newItem.name && oldItem.count == newItem.count

            override fun getChangePayload(oldItem: TallyItem, newItem: TallyItem): Any? {
                return if (oldItem.name == newItem.name && oldItem.count != newItem.count) {
                    PAYLOAD_COUNT
                } else null
            }
        }
    }

    init {
        setHasStableIds(true)
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    inner class VH(val binding: ItemSpeciesTileVt4Binding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.tileRoot.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = getItem(pos)
                onTileClick(item.speciesId)
            }
        }

        fun bindFull(item: TallyItem) = with(binding) {
            tvSpeciesName.text = item.name
            tvSpeciesCount.text = item.count.toString()
        }

        fun bindCountOnly(item: TallyItem) = with(binding) {
            tvSpeciesCount.text = item.count.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemSpeciesTileVt4Binding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bindFull(getItem(position))
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_COUNT)) {
            holder.bindCountOnly(getItem(position))
        } else {
            holder.bindFull(getItem(position))
        }
    }

    override fun getItemId(position: Int): Long =
        getItem(position).speciesId.hashCode().toLong()
}

data class TallyItem(
    val speciesId: String,
    val name: String,
    val count: Int
)
