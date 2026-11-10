package com.kostovskiapps.personalnotes.presentation.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.repository.ExportedNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    val note: Note? = null,
    val isPreparing: Boolean = false,
    val exported: ExportedNote? = null,
    val shareLink: String = "",
    val error: String? = null,
) {
    val canShareFiles: Boolean get() = exported != null
}

/**
 * Prepares one note for sending to another user of the app: it builds the `.pnote` file and,
 * independently of it, a deep link that already contains the note.
 */
class NoteShareViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    private var preparedNoteId: Long? = null

    /** Idempotent: reopening the sheet for the same note does not re-export it. */
    fun prepare(noteId: Long) {
        if (noteId == Note.NO_ID) return
        if (preparedNoteId == noteId && _state.value.note != null) return
        preparedNoteId = noteId
        _state.value = ShareUiState(isPreparing = true)
        viewModelScope.launch {
            val note = container.getNote(noteId)
            if (note == null) {
                _state.value = ShareUiState(error = "This note could not be found")
                return@launch
            }
            _state.update { it.copy(note = note, shareLink = container.buildNoteLink(note)) }
            export(note)
        }
    }
    private suspend fun export(note: Note) {
        _state.update { it.copy(isPreparing = true, error = null) }
        runCatching { container.exportNote(note) }
            .onSuccess { exported ->
                _state.update {
                    it.copy(
                        exported = exported,
                        shareLink = exported.shareLink,
                        isPreparing = false,
                        error = null,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isPreparing = false,
                        error = error.message ?: "The note could not be prepared for sharing",
                    )
                }
            }
    }

    fun reset() {
        preparedNoteId = null
        _state.value = ShareUiState()
    }

    fun retry() {
        val note = _state.value.note ?: return
        viewModelScope.launch { export(note) }
    }
}
