package com.kostovskiapps.personalnotes.domain.model

/** What the user typed/spoke into the editor, before it is persisted. */
data class NoteDraft(
    val id: Long = Note.NO_ID,
    val title: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val location: NoteLocation = NoteLocation.EMPTY,
    val color: NoteColor = NoteColor.DEFAULT,
    val kind: NoteKind = NoteKind.TYPED,
    val fileRefs: List<String> = emptyList(),
)

/** Sort orders offered on the notes list. */
enum class NoteSort(val id: String, val label: String) {
    NEWEST(id = "newest", label = "Newest first"),
    OLDEST(id = "oldest", label = "Oldest first"),
    TITLE(id = "title", label = "Title A-Z"),
    COLOR(id = "color", label = "By colour"),
    ;

    companion object {
        val DEFAULT: NoteSort = NEWEST

        fun fromId(id: String?): NoteSort = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** The filters applied on top of the stored notes. */
data class NoteFilter(
    val query: String = "",
    val color: NoteColor? = null,
    val sort: NoteSort = NoteSort.DEFAULT,
)
