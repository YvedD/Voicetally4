package com.yvesds.voicetally4.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yvesds.voicetally4.R
import com.yvesds.voicetally4.shared.SharedSpeciesViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * VT3-achtige popup om een teller aan te passen (±/reset/instellen).
 * Werkt rechtstreeks op SharedSpeciesViewModel.
 */
@AndroidEntryPoint
class SpeciesAdjustDialogFragment : DialogFragment() {

    private val sharedVm: SharedSpeciesViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val species = requireArguments().getString(ARG_SPECIES).orEmpty()

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_species_adjust_vt4, null, false)

        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvCurrent: TextView = view.findViewById(R.id.tvCurrent)
        val etSet: EditText = view.findViewById(R.id.etSet)
        val btnMinus: Button = view.findViewById(R.id.btnMinus)
        val btnPlus: Button = view.findViewById(R.id.btnPlus)
        val btnReset: Button = view.findViewById(R.id.btnReset)

        tvTitle.text = species

        fun refresh() {
            val cur = sharedVm.tallyMap.value[species] ?: 0
            tvCurrent.text = cur.toString()
        }
        refresh()

        btnMinus.setOnClickListener {
            sharedVm.decrement(species, 1)
            refresh()
        }
        btnPlus.setOnClickListener {
            sharedVm.increment(species, 1)
            refresh()
        }
        btnReset.setOnClickListener {
            sharedVm.reset(species)
            etSet.setText("")
            refresh()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val v = etSet.text?.toString()?.trim().orEmpty()
                if (v.isNotEmpty()) {
                    v.toIntOrNull()?.let { sharedVm.setCount(species, it) }
                }
            }
            .setNegativeButton(R.string.annuleer, null)
            .create()
    }

    companion object {
        private const val ARG_SPECIES = "species"
        fun newInstance(speciesName: String) = SpeciesAdjustDialogFragment().apply {
            arguments = bundleOf(ARG_SPECIES to speciesName)
        }
    }
}
