package com.kostovskiapps.personalnotes.domain.model

/**
 * A note as the rest of the app understands it, free of any storage detail.
 *
 * [fileRefs] holds app-private file names (never absolute paths) so a note stays portable
 * when the database is moved or backed up.
 */
data class Note(
    val id: Long = NO_ID,
    val title: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val location: NoteLocation = NoteLocation.EMPTY,
    val color: NoteColor = NoteColor.DEFAULT,
    val kind: NoteKind = NoteKind.TYPED,
    val fileRefs: List<String> = emptyList(),
) {
    val displayTitle: String get() = title.ifBlank { UNTITLED }

    val hasDescription: Boolean get() = description.isNotBlank()

    val isPersisted: Boolean get() = id != NO_ID

    /** First attached file, treated as the note's voice recording when present. */
    val voiceRef: String? get() = fileRefs.firstOrNull()

    val hasVoice: Boolean get() = voiceRef != null

    fun isEmpty(): Boolean = title.isBlank() && description.isBlank() && fileRefs.isEmpty()

    companion object {
        const val NO_ID = 0L
        const val UNTITLED = "Untitled note"
    }
}
