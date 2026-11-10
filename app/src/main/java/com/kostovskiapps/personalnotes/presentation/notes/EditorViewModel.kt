package com.kostovskiapps.personalnotes.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kostovskiapps.personalnotes.data.voice.DictationState
import com.kostovskiapps.personalnotes.data.voice.RecordingState
import com.kostovskiapps.personalnotes.data.voice.VoicePlayback
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteDraft
import com.kostovskiapps.personalnotes.domain.model.NoteKind
import com.kostovskiapps.personalnotes.domain.model.NoteLocation
import com.kostovskiapps.personalnotes.domain.usecase.UpsertNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the user is entering this note; drives which input tools are shown. */
enum class EditorMode { TYPING, SPEAKING }

data class EditorUiState(
    val id: Long = Note.NO_ID,
    val title: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val location: NoteLocation = NoteLocation.EMPTY,
    val color: NoteColor = NoteColor.DEFAULT,
    val kind: NoteKind = NoteKind.TYPED,
    val fileRefs: List<String> = emptyList(),
    val mode: EditorMode = EditorMode.TYPING,
    val isLoading: Boolean = false,
    val isLocating: Boolean = false,
    val recording: RecordingState = RecordingState.Idle,
    val dictation: DictationState = DictationState.Idle,
    val playback: VoicePlayback = VoicePlayback(),
    val message: String? = null,
    val savedId: Long? = null,
    /** Set when the editor wants the host to leave this screen; the host then pops it. */
    val exitRequested: Boolean = false,
) {
    val isNew: Boolean get() = id == Note.NO_ID

    val hasContent: Boolean
        get() = title.isNotBlank() || description.isNotBlank() || fileRefs.isNotEmpty()

    val isRecording: Boolean get() = recording is RecordingState.Recording

    val hasVoice: Boolean get() = fileRefs.isNotEmpty()
}

/**
 * Owns the state of one note being written: text, dictation, recording, location and colour.
 *
 * Inputs are deliberately additive: dictation appends to whatever is already written, so the
 * user can type a line and then speak the rest into the same note.
 */
class EditorViewModel(
    private val container: AppContainer,
    private val noteId: Long = Note.NO_ID,
    startInSpeakingMode: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EditorUiState(
            id = noteId,
            isLoading = noteId != Note.NO_ID,
            mode = if (startInSpeakingMode) EditorMode.SPEAKING else EditorMode.TYPING,
        ),
    )
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val recognizer = container.speechRecognizerFactory()
    private var lastDictatedText: String = ""

    init {
        if (noteId != Note.NO_ID) {
            viewModelScope.launch {
                val note = container.getNote(noteId)
                _state.update { current -> note?.toEditorState(current) ?: current.copy(isLoading = false) }
            }
        }
        observeRecording()
        observeDictation()
        observePlayback()
    }

    private fun observePlayback() {
        viewModelScope.launch {
            container.voicePlayer.state.collect { playback ->
                _state.update { it.copy(playback = playback) }
            }
        }
    }

    private fun observeRecording() {
        viewModelScope.launch {
            container.voiceRecorder.state.collect { recording ->
                _state.update { current ->
                    val refs = if (recording is RecordingState.Finished && recording.fileRef !in current.fileRefs) {
                        current.fileRefs + recording.fileRef
                    } else {
                        current.fileRefs
                    }
                    current.copy(
                        recording = recording,
                        fileRefs = refs,
                        message = (recording as? RecordingState.Failed)?.reason ?: current.message,
                    )
                }
                if (recording is RecordingState.Finished) {
                    container.voiceRecorder.consume()
                    _state.update { it.copy(recording = RecordingState.Idle) }
                }
            }
        }
    }

    private fun observeDictation() {
        viewModelScope.launch {
            recognizer.state.collect { dictation ->
                when (dictation) {
                    is DictationState.Heard -> applyDictation(dictation.text)
                    is DictationState.Failed -> _state.update { it.copy(message = dictation.reason) }
                    is DictationState.Notice -> Unit
                    else -> Unit
                }
                _state.update { it.copy(dictation = dictation) }
            }
        }
    }

    /**
     * Dictation replaces the text it produced last, so partial results grow the note instead
     * of duplicating it on every update from the recogniser.
     */
    private fun applyDictation(text: String) {
        _state.update { current ->
            val previous = lastDictatedText
            val base = if (previous.isNotEmpty() && current.description.endsWith(previous)) {
                current.description.dropLast(previous.length)
            } else {
                current.description
            }
            val separator = if (base.isEmpty() || base.endsWith(" ") || base.endsWith("\n")) "" else " "
            lastDictatedText = text
            current.copy(description = base + separator + text, kind = NoteKind.SPOKEN)
        }
    }

    fun onModeChange(mode: EditorMode) {
        _state.update { it.copy(mode = mode) }
        if (mode == EditorMode.TYPING) stopDictation()
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun onDescriptionChange(value: String) {
        // Manual edits invalidate the dictation diffing baseline.
        lastDictatedText = ""
        _state.update { it.copy(description = value) }
    }

    fun onColorChange(color: NoteColor) = _state.update { it.copy(color = color) }

    fun onLocationLabelChange(label: String) =
        _state.update { it.copy(location = it.location.copy(label = label)) }

    fun clearLocation() = _state.update { it.copy(location = NoteLocation.EMPTY) }

    // region dictation
    fun startDictation() {
        if (!recognizer.isAvailable) {
            _state.update { it.copy(message = "Speech recognition is not available on this device") }
            return
        }
        lastDictatedText = ""
        recognizer.start()
    }

    fun stopDictation() {
        if (recognizer.isListening) recognizer.stop() else recognizer.cancel()
    }

    fun toggleDictation() {
        if (recognizer.isListening) stopDictation() else startDictation()
    }

    fun dismissDictation() {
        recognizer.cancel()
        container.voiceRecorder.consume()
    }
    // endregion

    // region recording
    fun startRecording() {
        if (container.voiceRecorder.start()) {
            _state.update { it.copy(mode = EditorMode.SPEAKING) }
        }
    }

    fun stopRecording() = container.voiceRecorder.stop()

    fun cancelRecording() {
        container.voiceRecorder.discard()
        _state.update { it.copy(recording = RecordingState.Idle) }
    }

    fun removeVoiceRecording() {
        container.voicePlayer.stop()
        _state.update { it.copy(fileRefs = emptyList()) }
    }

    fun togglePlayback() {
        val ref = _state.value.fileRefs.firstOrNull() ?: return
        container.voicePlayer.attach(ref)
        container.voicePlayer.toggle()
    }

    fun seekPlayback(fraction: Float) = container.voicePlayer.seekTo(fraction)
    // endregion

    // region location
    fun captureLocation() {
        if (!container.locationProvider.hasPermission()) {
            _state.update { it.copy(message = "Location permission is needed to tag this note") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLocating = true) }
            val result = container.locationProvider.currentLocation()
            _state.update { current ->
                result.fold(
                    onSuccess = { location ->
                        current.copy(location = location, isLocating = false)
                    },
                    onFailure = { error ->
                        current.copy(
                            isLocating = false,
                            message = error.message ?: "The location could not be read",
                        )
                    },
                )
            }
        }
    }
    // endregion

    /** Explicit save: stays on the screen so the user can keep editing. */
    fun save() {
        viewModelScope.launch {
            val draft = currentDraft(withMessage = true) ?: return@launch
            val id = persist(draft)
            if (id != null) _state.update { it.copy(id = id, savedId = id, message = null) }
        }
    }

    /**
     * Leaving the editor saves silently, so a note is never lost by pressing back. The write is
     * handed to the container's application scope (and is non-cancellable), because this screen
     * is destroyed while it is still in flight.
     */
    fun saveAndExit() {
        val draft = currentDraft(withMessage = false)
        if (draft != null) container.persistNote(draft)
        _state.update { it.copy(exitRequested = true) }
    }

    /**
     * Persists the note and asks the host to open the share sheet for it. The real id is waited
     * for, because the share sheet loads the note from storage.
     */
    fun requestShare() {
        viewModelScope.launch {
            val draft = currentDraft(withMessage = true) ?: return@launch
            val id = persist(draft) ?: return@launch
            _state.update { it.copy(id = id, savedId = id, message = null) }
        }
    }

    /** The editor's current content as a draft, or null when there is nothing worth saving. */
    private fun currentDraft(withMessage: Boolean): NoteDraft? {
        val current = _state.value
        if (current.isRecording) {
            stopRecording()
            return null
        }
        if (!current.hasContent) {
            if (withMessage) {
                _state.update { it.copy(message = "Add a title, some text or a recording first") }
            }
            return null
        }
        return NoteDraft(
            id = current.id,
            title = current.title,
            description = current.description,
            createdAt = current.createdAt,
            location = current.location,
            color = current.color,
            kind = current.kind,
            fileRefs = current.fileRefs,
        )
    }

    private suspend fun persist(draft: NoteDraft): Long? =
        when (val outcome = container.persistNote(draft).await()) {
            is UpsertNoteUseCase.Outcome.Saved -> outcome.id

            UpsertNoteUseCase.Outcome.NothingToSave -> {
                _state.update { it.copy(message = "Nothing to save yet") }
                null
            }
        }
    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeSaved() = _state.update { it.copy(savedId = null) }

    fun consumeExit() = _state.update { it.copy(exitRequested = false) }

    override fun onCleared() {
        recognizer.release()
        container.voicePlayer.stop()
        super.onCleared()
    }

    private fun Note.toEditorState(current: EditorUiState): EditorUiState = current.copy(
        id = id,
        title = title,
        description = description,
        createdAt = createdAt,
        location = location,
        color = color,
        kind = kind,
        fileRefs = fileRefs,
        mode = if (fileRefs.isNotEmpty()) EditorMode.SPEAKING else EditorMode.TYPING,
        isLoading = false,
    )
}
