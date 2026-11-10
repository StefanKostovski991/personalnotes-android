package com.kostovskiapps.personalnotes.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's palette: a warm yellow gradient identity.
 *
 * Colours are declared here (not inline in composables) so the gradient, the note palette and
 * the Material colour scheme always stay in tune with each other.
 */
object NotesPalette {
    // Gradient stops, bright to deep - the signature look.
    val Sunlight = Color(0xFFFFF8DC)
    val Butter = Color(0xFFFFE9A3)
    val Gold = Color(0xFFFFC93C)
    val Amber = Color(0xFFFFA000)
    val HoneyDeep = Color(0xFFF57F17)
    val Caramel = Color(0xFFB35C00)

    // Ink.
    val Ink = Color(0xFF2E2506)
    val InkSoft = Color(0xFF6B5B22)
    val InkFaint = Color(0xFF9C8B4E)
    val Cream = Color(0xFFFFFDF5)
    val PaperWhite = Color(0xFFFFFFFF)

    // Accents used for state, not decoration.
    val Success = Color(0xFF2E7D32)
    val Danger = Color(0xFFC62828)
    val Info = Color(0xFF00796B)

    // Dark theme counterparts.
    val NightBackground = Color(0xFF17130A)
    val NightSurface = Color(0xFF241E10)
    val NightSurfaceHigh = Color(0xFF332B16)
    val NightPrimary = Color(0xFFFFD24D)
    val NightOnSurface = Color(0xFFF6E9C4)
    val NightOutline = Color(0xFF5A4C22)
}
