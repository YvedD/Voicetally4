package com.yvesds.voicetally4.ui.tally

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SimpleRecognitionListener(
    private val onReady: (() -> Unit)? = null,
    private val onPartial: ((String) -> Unit)? = null,
    private val onFinal: ((String) -> Unit)? = null,
    private val onError: ((Int) -> Unit)? = null,
    private val onEnd: (() -> Unit)? = null
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) { onReady?.invoke() }
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { onEnd?.invoke() }

    override fun onError(error: Int) {
        onError?.invoke(error)
        onEnd?.invoke()
    }

    override fun onResults(results: Bundle?) {
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = texts?.firstOrNull().orEmpty()
        onFinal?.invoke(text)
        onEnd?.invoke()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = texts?.firstOrNull().orEmpty()
        if (text.isNotEmpty()) onPartial?.invoke(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
