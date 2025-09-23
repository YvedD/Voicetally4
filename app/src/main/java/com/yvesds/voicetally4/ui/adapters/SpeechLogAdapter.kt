package com.yvesds.voicetally4.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.voicetally4.databinding.ItemLogLineBinding

/**
 * Simpele adapter voor het log-venster (bovenaan het Tally-scherm).
 * Gebruikt item_log_line.xml (TextView met id tvLogLine).
 */
class SpeechLogAdapter : RecyclerView.Adapter<SpeechLogAdapter.VH>() {

    private val items = ArrayList<String>()

    class VH(val binding: ItemLogLineBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        val binding = ItemLogLineBinding.inflate(inf, parent, false)
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.binding.tvLogLine.text = items[position]
    }

    fun setAll(lines: List<String>) {
        items.clear()
        items.addAll(lines)
        notifyDataSetChanged()
    }

    fun add(line: String) {
        val insertAt = items.size
        items.add(line)
        notifyItemInserted(insertAt)
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }
}
