package com.kostovskiapps.personalnotes.domain.model

/** Where an incoming shared note came from, so the UI can explain what happened. */
enum class NoteImportSource {
    FILE,
    LINK,
    SHARE_INTENT,
    UNKNOWN,
}

/** Outcome of ingesting a note that was shared by another user of the app. */
sealed interface NoteImportResult {
    data class Imported(val note: Note) : NoteImportResult

    /** The note was already stored; [note] is the existing copy. */
    data class AlreadyExists(val note: Note) : NoteImportResult

    data class Failed(val reason: String) : NoteImportResult
}
