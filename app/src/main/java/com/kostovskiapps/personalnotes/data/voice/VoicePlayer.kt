package com.kostovskiapps.personalnotes.data.voice

import android.media.MediaPlayer
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

/** Playback state of the currently opened note's recording. */
data class VoicePlayback(
    val fileRef: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
) {
    val hasTrack: Boolean get() = fileRef != null

    val progress: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Plays the recording attached to the currently open note.
 *
 * A single instance is shared through the container so navigating between notes never leaves a
 * stray MediaPlayer running.
 */
class VoicePlayer(
    private val attachments: AttachmentStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(VoicePlayback())
    val state: StateFlow<VoicePlayback> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Declares which recording the screen is showing; stops playback when it changes. */
    fun attach(fileRef: String?, durationHintMs: Long = 0L) {
        if (_state.value.fileRef == fileRef) return
        stop()
        _state.value = VoicePlayback(fileRef = fileRef, durationMs = durationHintMs)
    }

    fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun play() {
        val fileRef = _state.value.fileRef ?: return
        if (player != null) {
            runCatching { player?.start() }
            _state.value = _state.value.copy(isPlaying = true, error = null)
            startTicker()
            return
        }
        val file = attachments.resolve(fileRef)
        if (file == null || !file.exists()) {
            _state.value = _state.value.copy(error = "The recording is missing", isPlaying = false)
            return
        }
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                    _state.value = _state.value.copy(positionMs = 0L)
                }
                start()
            }
        }.getOrElse { error ->
            _state.value = _state.value.copy(
                error = error.message ?: "This recording cannot be played",
                isPlaying = false,
            )
            return
        }
        _state.value = _state.value.copy(
            isPlaying = true,
            durationMs = player?.duration?.toLong()?.takeIf { it > 0 } ?: _state.value.durationMs,
            error = null,
        )
        startTicker()
    }

    fun pause() {
        runCatching { player?.pause() }
        ticker?.cancel()
        ticker = null
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun seekTo(fraction: Float) {
        val duration = _state.value.durationMs
        val active = player ?: return
        if (duration <= 0L) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toInt()
        runCatching { active.seekTo(target) }
        _state.value = _state.value.copy(positionMs = target.toLong())
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        val active = player
        player = null
        runCatching {
            active?.stop()
            active?.release()
        }
        _state.value = _state.value.copy(isPlaying = false)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player != null) {
                delay(TICK_MS)
                val active = player ?: break
                val position = runCatching { active.currentPosition.toLong() }.getOrDefault(0L)
                _state.value = _state.value.copy(positionMs = position)
            }
        }
    }

    companion object {
        private const val TICK_MS = 200L
    }
}
