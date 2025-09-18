package com.yvesds.voicetally4.ui.schermen

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.Serializable

/**
 * Modale dialoog: toonde herkende NIET-actieve soorten met aantallen.
 * Gebruiker kan aanvinken welke toegevoegd & geboekt moeten worden.
 */
class NieuweSoortenDialoogScherm : DialogFragment() {

    data class Proposal(val speciesId: String, val displayName: String, val amount: Int) : Serializable

    interface Listener {
        fun onNieuweSoortenGekozen(selected: List<Proposal>)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val props = (requireArguments().getSerializable(ARG_PROPOSALS) as? ArrayList<Proposal>) ?: arrayListOf()
        val labels = props.map { "${it.displayName} → +${it.amount}" }.toTypedArray()
        val checked = BooleanArray(props.size) { true }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Gevonden niet-actieve soorten")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Toevoegen") { _, _ ->
                val selected = props.filterIndexed { index, _ -> checked[index] }
                (parentFragment as? Listener)?.onNieuweSoortenGekozen(selected)
            }
            .setNegativeButton("Negeer", null)
            .create()
    }

    companion object {
        private const val ARG_PROPOSALS = "arg_proposals"

        fun newInstance(list: List<Proposal>): NieuweSoortenDialoogScherm {
            val f = NieuweSoortenDialoogScherm()
            f.arguments = Bundle().apply {
                putSerializable(ARG_PROPOSALS, ArrayList(list))
            }
            return f
        }
    }
}
