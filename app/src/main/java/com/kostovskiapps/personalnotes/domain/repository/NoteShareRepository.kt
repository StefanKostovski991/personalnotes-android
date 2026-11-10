package com.kostovskiapps.personalnotes.domain.repository

import com.kostovskiapps.personalnotes.domain.model.Note

/** Result of exporting one note to a file the user can hand to somebody else. */
data class ExportedNote(
    val fileUriString: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Self-contained link that carries the whole note, usable without a file transfer. */
    val shareLink: String,
)

/**
 * Port for handing a note to the outside world: writing the `.pnote` file, building the
 * deep link and letting the system share sheet pick the receiving app.
 */
interface NoteShareRepository {
    suspend fun exportNote(note: Note): ExportedNote

    fun buildShareLink(note: Note): String

    /** Copies an app-private attachment (the voice recording) into a readable cache file. */
    suspend fun resolveAttachment(fileRef: String): SharedAttachment?
}

/** A file that can be attached to a share intent. */
data class SharedAttachment(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
)
