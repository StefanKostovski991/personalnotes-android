package com.kostovskiapps.personalnotes.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = NotesPalette.Amber,
    onPrimary = Color.White,
    primaryContainer = NotesPalette.Butter,
    onPrimaryContainer = NotesPalette.Ink,
    secondary = NotesPalette.HoneyDeep,
    onSecondary = Color.White,
    secondaryContainer = NotesPalette.Sunlight,
    onSecondaryContainer = NotesPalette.Ink,
    tertiary = NotesPalette.Caramel,
    onTertiary = Color.White,
    background = NotesPalette.Sunlight,
    onBackground = NotesPalette.Ink,
    surface = NotesPalette.Cream,
    onSurface = NotesPalette.Ink,
    surfaceVariant = NotesPalette.Butter,
    onSurfaceVariant = NotesPalette.InkSoft,
    outline = NotesPalette.InkFaint,
    outlineVariant = NotesPalette.Butter,
    error = NotesPalette.Danger,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = NotesPalette.NightPrimary,
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF54420A),
    onPrimaryContainer = NotesPalette.NightOnSurface,
    secondary = NotesPalette.Gold,
    onSecondary = Color(0xFF3A2A00),
    secondaryContainer = Color(0xFF40340F),
    onSecondaryContainer = NotesPalette.NightOnSurface,
    tertiary = NotesPalette.Amber,
    onTertiary = Color(0xFF3A2A00),
    background = NotesPalette.NightBackground,
    onBackground = NotesPalette.NightOnSurface,
    surface = NotesPalette.NightSurface,
    onSurface = NotesPalette.NightOnSurface,
    surfaceVariant = NotesPalette.NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFD6C79A),
    outline = NotesPalette.NightOutline,
    outlineVariant = NotesPalette.NightSurfaceHigh,
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
)

/**
 * The gradient brushes used across the app. Exposed through a composition local so screens do
 * not hard-code colours and the whole identity can be re-themed in one place.
 */
data class NotesGradients(
    val signature: Brush,
    val subtle: Brush,
    val card: Brush,
    val glow: Brush,
)

val LocalNotesGradients = staticCompositionLocalOf<NotesGradients> {
    error("NotesGradients was not provided")
}

private fun gradientsFor(dark: Boolean): NotesGradients = if (dark) {
    NotesGradients(
        signature = Brush.linearGradient(
            listOf(Color(0xFF6B4E00), Color(0xFF4A3606), Color(0xFF241E10)),
        ),
        subtle = Brush.verticalGradient(
            listOf(Color(0xFF1F1A0C), Color(0xFF17130A)),
        ),
        card = Brush.linearGradient(
            listOf(Color(0xFF33290F), Color(0xFF2A2210)),
        ),
        glow = Brush.radialGradient(
            listOf(NotesPalette.Gold.copy(alpha = 0.22f), Color.Transparent),
        ),
    )
} else {
    NotesGradients(
        signature = Brush.linearGradient(
            listOf(NotesPalette.Butter, NotesPalette.Gold, NotesPalette.Amber),
        ),
        subtle = Brush.verticalGradient(
            listOf(NotesPalette.Sunlight, NotesPalette.Cream, Color(0xFFFFF4CE)),
        ),
        card = Brush.linearGradient(
            listOf(NotesPalette.PaperWhite, Color(0xFFFFF7E0)),
        ),
        glow = Brush.radialGradient(
            listOf(NotesPalette.Amber.copy(alpha = 0.28f), Color.Transparent),
        ),
    )
}

@Composable
fun PersonalNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(LocalNotesGradients provides gradientsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = NotesTypography,
            shapes = NotesShapes,
            content = content,
        )
    }
}

/**
 * Convenience: the app-wide background gradient behind any screen.
 *
 * The gradient itself is drawn edge to edge, but [content] is inset by the safe drawing area on
 * the top and sides, so every screen's top bar clears the status bar and any display cutout.
 *
 * The top inset matters more than it looks: on gesture navigation the system reserves a taller
 * band along the top edge (`mandatorySystemGestures`, ~40dp here) than the status bar itself
 * (~26dp), and anything drawn inside that band is visible but not tappable. That is exactly how
 * the editor's back button became unusable.
 *
 * The bottom inset is left to the screens: their bottom-anchored actions get padding from
 * Scaffold and the navigation bar.
 */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    brush: Brush? = null,
    applySafeArea: Boolean = true,
    content: @Composable () -> Unit,
) {
    val gradients = LocalNotesGradients.current
    val safeArea = WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush ?: gradients.subtle),
    ) {
        Box(
            modifier = if (applySafeArea) {
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(safeArea)
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            content()
        }
    }
}
