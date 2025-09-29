package com.yvesds.voicetally4.ui.adapters

import android.os.Build
import android.text.Layout
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.databinding.ItemAliasTileBinding
import com.yvesds.voicetally4.ui.data.SoortAlias

/**
 * Tile-adapter voor soortselectie:
 * - Selectie blijft keyed op tileName (zoals voorheen)
 * - Weergave = CANONICAL (gevraagd gedrag)
 * - 2 regels + ellipsize + autosize
 */
class AliasTileAdapter(
    private val isSelected: (String) -> Boolean, // key = tileName
    private val onToggle: (String) -> Unit       // key = tileName
) : ListAdapter<SoortAlias, AliasTileAdapter.VH>(DIFF) {

    companion object {
        private object PayloadSelection

        private val DIFF = object : DiffUtil.ItemCallback<SoortAlias>() {
            override fun areItemsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem.tileName == newItem.tileName

            override fun areContentsTheSame(oldItem: SoortAlias, newItem: SoortAlias): Boolean =
                oldItem == newItem
        }

        /** Stabiele 64-bit FNV-1a hash op key (tileName), identiek aan je vorige aanpak. */
        private fun stableIdFromKey(key: String): Long {
            var hash = -3750763034362895579L // 0xCBF29CE484222325 (signed long)
            val prime = 1099511628211L       // 0x00000100000001B3
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
            // Eén listener per ViewHolder
            btn.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = getItem(pos)
                onToggle(item.tileName) // selectie blijft op tileName
                this@AliasTileAdapter.notifyItemChanged(pos, PayloadSelection)
            }

            // Tekstinstellingen (eenmalig per holder)
            btn.isAllCaps = false
            btn.maxLines = 2
            btn.ellipsize = TextUtils.TruncateAt.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                btn.breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                btn.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            }
            // Uniform autosize 12–18sp
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                btn, 12, 18, 1, TypedValue.COMPLEX_UNIT_SP
            )
        }

        fun bindFull(item: SoortAlias) = with(btn) {
            // **Weergave = canonical**, volgens jouw wens
            text = item.canonical
            isChecked = isSelected(item.tileName)
        }

        fun bindSelectionOnly(item: SoortAlias) {
            btn.isChecked = isSelected(item.tileName)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAliasTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
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
