package com.kostovskiapps.personalnotes.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kostovskiapps.personalnotes.R

/**
 * The app mark: a ruled sheet of paper with a pencil resting across it.
 *
 * One composable keeps the logo identical everywhere it appears (splash, list header) and gives
 * it a screen-reader label.
 */
@Composable
fun NotesLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentDescription: String = "Personal Notes",
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    )
}

/**
 * The artwork only fills part of the 108x108 icon viewport (the rest of that space is the
 * adaptive-icon safe zone). Asking for [visibleSize] on screen therefore needs a larger box.
 */
fun logoBoxFor(visibleSize: Dp): Dp = visibleSize * 108f / 92f
