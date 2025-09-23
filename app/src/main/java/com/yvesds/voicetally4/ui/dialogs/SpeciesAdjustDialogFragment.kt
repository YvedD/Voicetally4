package com.yvesds.voicetally4.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.yvesds.voicetally4.ui.shared.SharedSpeciesViewModel
import com.yvesds.voicetally4.ui.tally.TallyViewModel

/**
 * Eenvoudige aanpas-dialog voor één soort:
 * - Toont displayName (of canonical)
 * - [+] [-] [reset] knoppen
 * - Directe invoer van een nieuw aantal
 * - OK schrijft via TallyViewModel.setCount(...)
 *
 * Gebruik: SpeciesAdjustDialogFragment.newInstance(canonical).show(...)
 */
class SpeciesAdjustDialogFragment : DialogFragment() {

    private val sharedVm: SharedSpeciesViewModel by activityViewModels()
    private val tallyVm: TallyViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val canonical = requireArguments().getString(ARG_CANONICAL) ?: ""

        // Titel opbouwen (display name indien bekend)
        val display = sharedVm.displayNames.value[canonical] ?: canonical
        val current = tallyVm.getCount(canonical)

        // UI container
        val dp = ctx.resources.displayMetrics.density
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), 0)
        }

        // Naam + huidig aantal
        val nameView = TextView(ctx).apply {
            text = display
            textSize = 18f
        }
        val countView = TextView(ctx).apply {
            text = "Huidig: $current"
            textSize = 14f
        }

        // Input veld om exact aantal te zetten
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(7))
            hint = "Nieuw aantal…"
        }

        // Knoppenrij: [-]  [reset]  [+]
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val btnDec = MaterialButton(ctx).apply { text = "−" }
            val btnRst = MaterialButton(ctx).apply { text = "reset" }
            val btnInc = MaterialButton(ctx).apply { text = "+" }

            fun bump(delta: Int) {
                val cur = tallyVm.getCount(canonical)
                val next = (cur + delta).coerceAtLeast(0)
                tallyVm.setCount(canonical, next)
                countView.text = "Huidig: $next"
            }

            btnDec.setOnClickListener { bump(-1) }
            btnInc.setOnClickListener { bump(+1) }
            btnRst.setOnClickListener {
                tallyVm.reset(canonical)
                countView.text = "Huidig: 0"
            }

            addView(btnDec, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnRst, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(btnInc, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        container.addView(nameView)
        container.addView(countView)
        container.addView(row)
        container.addView(input)

        return AlertDialog.Builder(ctx)
            .setTitle("Telling aanpassen")
            .setView(container)
            .setNegativeButton("Annuleren", null)
            .setPositiveButton("OK") { _, _ ->
                val txt = input.text?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    val number = txt.toIntOrNull()
                    if (number != null && number >= 0) {
                        tallyVm.setCount(canonical, number)
                    }
                }
            }
            .create()
    }

    companion object {
        private const val ARG_CANONICAL = "arg.canonical"

        fun newInstance(canonical: String) = SpeciesAdjustDialogFragment().apply {
            arguments = bundleOf(ARG_CANONICAL to canonical)
        }
    }
}
