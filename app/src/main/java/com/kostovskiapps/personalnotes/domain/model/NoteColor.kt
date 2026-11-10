package com.kostovskiapps.personalnotes.domain.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The palette a note can be tagged with. Every entry is a warm yellow-family tone so the
 * whole app keeps one visual identity, while still giving the user a way to colour-code.
 *
 * Stored in the database as the enum [id], never as an ordinal, so the palette can grow.
 */
enum class NoteColor(
    val id: String,
    val label: String,
    val accent: Long,
    val surface: Long,
) {
    SUNBEAM(id = "sunbeam", label = "Sunbeam", accent = 0xFFF9A825, surface = 0xFFFFF3C4),
    AMBER(id = "amber", label = "Amber", accent = 0xFFEF6C00, surface = 0xFFFFE7C2),
    HONEY(id = "honey", label = "Honey", accent = 0xFFC58A00, surface = 0xFFFDF0CE),
    LEMON(id = "lemon", label = "Lemon", accent = 0xFF9E9D24, surface = 0xFFF7F6C8),
    CREAM(id = "cream", label = "Cream", accent = 0xFF8D6E63, surface = 0xFFF6ECDD),
    ;

    val accentColor: Color get() = Color(accent)

    val surfaceColor: Color get() = Color(surface)

    /** Dark ink that stays readable on top of [surfaceColor]. */
    val onSurfaceColor: Color
        get() = if (surfaceColor.luminance() > 0.5f) Color(0xFF3A2E00) else Color(0xFFFFFFFF)

    companion object {
        val DEFAULT: NoteColor = SUNBEAM

        fun fromId(id: String?): NoteColor = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
