package com.kostovskiapps.personalnotes.presentation.notes

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kostovskiapps.personalnotes.core.DeepLinkParser
import com.kostovskiapps.personalnotes.data.codec.NotePayloadCodec
import com.kostovskiapps.personalnotes.di.AppContainer
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteImportResult
import com.kostovskiapps.personalnotes.domain.model.NoteImportSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One note that arrived from another user. */
data class ReceivedNote(
    val note: Note,
    val alreadyExisted: Boolean,
)

data class IncomingUiState(
    val isWorking: Boolean = false,
    val received: List<ReceivedNote> = emptyList(),
    val openedNoteId: Long? = null,
    val error: String? = null,
)

/**
 * Handles every way a note can arrive: a deep link that carries the note, a `.pnote` document
 * the user tapped, or a share intent from another app.
 *
 * The intent is reduced to a plain description here so the ViewModel stays free of Activity
 * lifecycles and the work survives recomposition.
 */
class IncomingNoteViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(IncomingUiState())
    val state: StateFlow<IncomingUiState> = _state.asStateFlow()

    private var handledToken: String? = null

    fun handle(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.parcelableUri(Intent.EXTRA_STREAM)
                importDocuments(listOfNotNull(uri))
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                importDocuments(uris)
            }

            Intent.ACTION_VIEW -> handleView(intent)
            else -> handleView(intent)
        }
    }

    /** Kept separate so a re-delivered intent (rotation) is not imported twice. */
    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = container.context.contentResolver
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            val results = mutableListOf<ReceivedNote>()
            var failure: String? = null
            for (uri in uris) {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        resolver.openInputStream(uri)?.use { stream ->
                            container.importNote(stream, NoteImportSource.FILE)
                        } ?: NoteImportResult.Failed("The file could not be opened")
                    }.getOrElse { error ->
                        NoteImportResult.Failed(error.message ?: "The file could not be read")
                    }
                }
                when (outcome) {
                    is NoteImportResult.Imported -> results += ReceivedNote(outcome.note, false)
                    is NoteImportResult.AlreadyExists -> results += ReceivedNote(outcome.note, true)
                    is NoteImportResult.Failed -> failure = outcome.reason
                }
            }
            _state.update { current ->
                current.copy(
                    isWorking = false,
                    received = current.received + results,
                    // A single imported note is opened straight away; several are listed.
                    openedNoteId = results.singleOrNull()?.takeIf { uris.size == 1 }?.note?.id,
                    error = failure,
                )
            }
        }
    }

    private fun handleView(intent: Intent) {
        val data = intent.data ?: return
        when (val incoming = DeepLinkParser.parse(data.toString(), intent.type)) {
            is DeepLinkParser.Incoming.LocalNote ->
                _state.update { it.copy(openedNoteId = incoming.id, error = null) }

            is DeepLinkParser.Incoming.EmbeddedNote -> importEmbedded(incoming.token)

            is DeepLinkParser.Incoming.NoteDocument -> importDocuments(listOf(data))

            null -> Unit
        }
    }

    private fun importEmbedded(token: String) {
        if (handledToken == token) return
        handledToken = token
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, error = null) }
            val payload = withContext(Dispatchers.IO) {
                runCatching { NotePayloadCodec.decodeFromLinkToken(token) }.getOrNull()
            }
            if (payload == null) {
                _state.update {
                    it.copy(isWorking = false, error = "This note link could not be opened")
                }
                return@launch
            }
            val json = withContext(Dispatchers.IO) {
                NotePayloadCodec.encodeToFileBytes(payload)
            }
            val outcome = withContext(Dispatchers.IO) {
                container.importNote(json.inputStream(), NoteImportSource.LINK)
            }
            _state.update { current ->
                when (outcome) {
                    is NoteImportResult.Imported -> current.copy(
                        isWorking = false,
                        received = current.received + ReceivedNote(outcome.note, false),
                        openedNoteId = outcome.note.id,
                    )

                    is NoteImportResult.AlreadyExists -> current.copy(
                        isWorking = false,
                        received = current.received + ReceivedNote(outcome.note, true),
                        openedNoteId = outcome.note.id,
                    )

                    is NoteImportResult.Failed -> current.copy(
                        isWorking = false,
                        error = outcome.reason,
                    )
                }
            }
        }
    }

    fun consumeOpenedNote() = _state.update { it.copy(openedNoteId = null) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun clearReceived() = _state.update { it.copy(received = emptyList()) }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableUri(key: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Uri::class.java)
        } else {
            getParcelableExtra(key)
        }
}
