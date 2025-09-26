package com.yvesds.voicetally4.ui.schermen

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize

/**
 * Modale dialoog: toont herkende NIET-actieve soorten met aantallen.
 * Gebruiker kan aanvinken welke toegevoegd & geboekt moeten worden.
 */
class NieuweSoortenDialoogScherm : DialogFragment() {

    @Parcelize
    data class Proposal(
        val speciesId: String,
        val displayName: String,
        val amount: Int
    ) : Parcelable

    interface Listener {
        fun onNieuweSoortenGekozen(selected: List<Proposal>)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val props: ArrayList<Proposal> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireArguments().getParcelableArrayList(ARG_PROPOSALS, Proposal::class.java)
                    ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                requireArguments().getParcelableArrayList(ARG_PROPOSALS) ?: arrayListOf()
            }

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
            return NieuweSoortenDialoogScherm().apply {
                arguments = Bundle().apply {
                    putParcelableArrayList(ARG_PROPOSALS, ArrayList(list))
                }
            }
        }
    }
}
