package com.kostovskiapps.personalnotes.data.voice

import android.media.MediaRecorder
import com.kostovskiapps.personalnotes.data.share.AttachmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Lifecycle of a voice recording, observed by the editor. */
sealed interface RecordingState {
    data object Idle : RecordingState

    data class Recording(val elapsedMs: Long, val amplitude: Float) : RecordingState

    data class Finished(val fileRef: String, val durationMs: Long) : RecordingState

    data class Failed(val reason: String) : RecordingState
}

/**
 * Records a spoken note to an app-private file.
 *
 * MediaRecorder is platform API, so it lives here behind a tiny state machine instead of in a
 * ViewModel; the editor only sees [RecordingState].
 */
class VoiceRecorder(
    private val attachments: AttachmentStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var ticker: Job? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = _state.value is RecordingState.Recording

    @Suppress("DEPRECATION")
    fun start(): Boolean {
        if (isRecording) return true
        val file = attachments.newVoiceFile()

        val created = try {
            // MediaRecorder(Context) is API 31+, so the no-arg constructor is used on API 30.
            @Suppress("DEPRECATION")
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Exception) {
            file.delete()
            _state.value = RecordingState.Failed(error.message ?: "Recording could not start")
            return false
        }

        recorder = created
        outputFile = file
        startedAt = System.currentTimeMillis()
        _state.value = RecordingState.Recording(elapsedMs = 0L, amplitude = 0f)
        startTicker()
        return true
    }

    fun stop() {
        val active = recorder ?: return
        recorder = null
        ticker?.cancel()
        ticker = null

        val duration = System.currentTimeMillis() - startedAt
        val file = outputFile
        outputFile = null

        val stoppedCleanly = runCatching {
            active.stop()
            active.release()
        }.isSuccess

        val recorded = file?.takeIf { it.exists() && it.length() > 0L }
        _state.value = when {
            !stoppedCleanly && recorded == null ->
                RecordingState.Failed("The recording could not be saved")

            recorded == null ->
                RecordingState.Failed("Nothing was recorded")

            else -> RecordingState.Finished(
                fileRef = recorded.name,
                durationMs = duration.coerceAtLeast(1L),
            )
        }
    }

    /** Drops the current recording without keeping the file. */
    fun discard() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
        ticker?.cancel()
        ticker = null
        outputFile?.delete()
        outputFile = null
        _state.value = RecordingState.Idle
    }

    /** Called after the recording has been stored with the note. */
    fun consume() {
        _state.value = RecordingState.Idle
    }

    fun fail(reason: String) {
        _state.value = RecordingState.Failed(reason)
    }

    private fun startTicker() {
        ticker = scope.launch {
            while (isActive && recorder != null) {
                delay(TICK_MS)
                val elapsed = System.currentTimeMillis() - startedAt
                _state.value = RecordingState.Recording(
                    elapsedMs = elapsed,
                    amplitude = readAmplitude(),
                )
            }
        }
    }

    private fun readAmplitude(): Float {
        val peak = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        // maxAmplitude is 0..32767; normalise to 0..1 for the waveform.
        return (peak / 32_767f).coerceIn(0f, 1f)
    }

    companion object {
        private const val TICK_MS = 100L
    }
}
