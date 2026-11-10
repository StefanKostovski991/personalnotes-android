package com.kostovskiapps.personalnotes.domain.model

/**
 * A place a note was written at. Coordinates are optional because the user may type an
 * address by hand, or deny the location permission.
 */
data class NoteLocation(
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null

    val isEmpty: Boolean get() = label.isBlank() && !hasCoordinates

    companion object {
        val EMPTY = NoteLocation(label = "")
    }
}
