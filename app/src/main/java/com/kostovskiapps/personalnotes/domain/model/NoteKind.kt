package com.kostovskiapps.personalnotes.domain.model

/** Where a note's text came from. Used for filtering and for the card badge. */
enum class NoteKind(val id: String, val label: String) {
    TYPED(id = "typed", label = "Typed"),
    SPOKEN(id = "spoken", label = "Spoken"),
    MIXED(id = "mixed", label = "Typed + spoken"),
    ;

    companion object {
        fun fromId(id: String?): NoteKind = entries.firstOrNull { it.id == id } ?: TYPED
    }
}
