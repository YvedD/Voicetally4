package com.yvesds.voicetally4.utils.hardware

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Eenvoudige, process-brede dispatcher voor hardware volume-up events.
 * Fragments kunnen register/unregister doen; StartActiviteit stuurt events door.
 */
object HardwareKeyHandler {

    interface Callback {
        /** Return true als het event is verbruikt. */
        fun onHardwareVolumeUp(): Boolean
    }

    private val currentCbRef = AtomicReference<WeakReference<Callback>?>(null)

    fun register(cb: Callback) {
        currentCbRef.set(WeakReference(cb))
    }

    fun unregister(cb: Callback) {
        val existing = currentCbRef.get()?.get()
        if (existing === cb) currentCbRef.set(null)
    }

    /** Door StartActiviteit aangeroepen bij volume-up. */
    fun dispatchVolumeUp(): Boolean {
        return currentCbRef.get()?.get()?.onHardwareVolumeUp() ?: false
    }
}
