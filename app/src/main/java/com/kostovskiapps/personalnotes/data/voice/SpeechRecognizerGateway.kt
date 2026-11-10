package com.kostovskiapps.personalnotes.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/** Dictation state exposed to the editor. */
sealed interface DictationState {
    data object Idle : DictationState

    data object Listening : DictationState

    data class Heard(val text: String) : DictationState

    /** Transient status such as "speak now"; the session stays open. */
    data class Notice(val message: String) : DictationState

    data class Failed(val reason: String) : DictationState
}

/**
 * Wraps the platform [SpeechRecognizer].
 *
 * The editor keeps one instance per dictation session because Android's recognizer is a
 * stateful, single-shot service: it delivers partial results while the user speaks and a final
 * result when they stop.
 */
class SpeechRecognizerGateway(context: Context) {

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var sessionId: String = UUID.randomUUID().toString()

    private val _state = MutableStateFlow<DictationState>(DictationState.Idle)
    val state: StateFlow<DictationState> = _state.asStateFlow()

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    val isListening: Boolean get() = _state.value is DictationState.Listening

    /** Current session, so late callbacks from an abandoned session can be ignored. */
    val currentSession: String get() = sessionId

    fun start() {
        if (!isAvailable) {
            _state.value = DictationState.Failed("Speech recognition is not available on this device")
            return
        }
        release()
        sessionId = UUID.randomUUID().toString()
        _state.value = DictationState.Listening
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(Listener(sessionId))
            startListening(buildIntent())
        }
    }

    /** Ends the session after a final result. */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        release()
        _state.value = DictationState.Idle
    }

    fun consume() {
        _state.value = DictationState.Idle
    }

    fun fail(reason: String) {
        _state.value = DictationState.Failed(reason)
    }

    fun release() {
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }
        recognizer = null
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        }

    private fun isCurrent(id: String): Boolean = id == sessionId

    private inner class Listener(private val id: String) : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            if (!isCurrent(id)) return
            _state.value = DictationState.Notice("Listening...")
        }

        override fun onBeginningOfSpeech() {
            if (!isCurrent(id)) return
            _state.value = DictationState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (!isCurrent(id)) return
            _state.value = DictationState.Notice("Working on it...")
        }

        override fun onError(error: Int) {
            if (!isCurrent(id)) return
            _state.value = DictationState.Failed(describe(error))
        }

        override fun onResults(results: Bundle?) {
            if (!isCurrent(id)) return
            val text = results.bestText()
            _state.value = if (text.isBlank()) {
                DictationState.Notice("Nothing was recognised - try again")
            } else {
                DictationState.Heard(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrent(id)) return
            val text = partialResults.bestText()
            if (text.isNotBlank()) _state.value = DictationState.Heard(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.bestText(): String = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "The recogniser was interrupted"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "No network connection for dictation"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Dictation timed out"
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing was recognised - try again"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy"
        SpeechRecognizer.ERROR_SERVER -> "The dictation service reported an error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was detected"
        else -> "Dictation failed ($error)"
    }
}
