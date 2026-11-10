package com.kostovskiapps.personalnotes.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteKind
import com.kostovskiapps.personalnotes.presentation.theme.LocalNotesGradients
import com.kostovskiapps.personalnotes.presentation.theme.NotesPalette
import com.kostovskiapps.personalnotes.core.DateTimeFormatter

/** Small pill label used for time, place and colour hints on a note. */
@Composable
fun MetaChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current,
    background: Color = Color.Transparent,
) {
    if (text.isBlank()) return
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NotesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search title, text or place",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Clear search",
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onQueryChange("") },
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/**
 * A round colour swatch. The selected swatch grows and gains a check mark, which reads better
 * than colour alone for accessibility.
 */
@Composable
fun ColorSwatch(
    color: NoteColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 34,
) {
    val animatedSize by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (selected) (size + 6).dp else size.dp,
        label = "swatchSize",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
        label = "swatchBorder",
    )
    Box(
        modifier = modifier
            .size(animatedSize)
            .clip(CircleShape)
            .background(color.surfaceColor)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor.copy(alpha = if (selected) 1f else 0.25f)), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = color.label,
                tint = color.onSurfaceColor,
                modifier = Modifier.size((size / 2).dp),
            )
        }
    }
}

/** The kind badge (typed / spoken) shown on a card. */
@Composable
fun KindBadge(kind: NoteKind, tint: Color, modifier: Modifier = Modifier) {
    val icon = when (kind) {
        NoteKind.SPOKEN -> Icons.Filled.Mic
        NoteKind.MIXED -> Icons.Filled.GraphicEq
        NoteKind.TYPED -> Icons.Filled.EditNote
    }
    Icon(
        imageVector = icon,
        contentDescription = kind.label,
        tint = tint,
        modifier = modifier.size(16.dp),
    )
}

/**
 * One note in the list. A soft gradient card with a thick colour rail on the leading edge and
 * an entrance animation driven by [index].
 */
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
) {
    val gradients = LocalNotesGradients.current
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(gradients.card)
            .border(BorderStroke(1.dp, note.color.accentColor.copy(alpha = 0.35f)), shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Lets the colour rail stretch to the card's own height instead of a fixed value.
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(note.color.accentColor),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    KindBadge(kind = note.kind, tint = note.color.accentColor)
                }

                if (note.hasDescription) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = note.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MetaChip(
                        icon = Icons.Filled.Check,
                        text = DateTimeFormatter.noteTimestamp(note.updatedAt, now),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    )
                    if (note.location.label.isNotBlank()) {
                        MetaChip(
                            icon = Icons.Filled.LocationOn,
                            text = note.location.label,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pulsing microphone halo used by the recorder. The pulse is decorative: the amplitude is
 * added on top so the halo reacts to how loudly the user is speaking.
 */
@Composable
fun PulsingMic(
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "micPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulse",
    )
    val scale = pulse + amplitude * 0.25f

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((96 * scale).dp)
                .clip(CircleShape)
                .background(NotesPalette.Amber.copy(alpha = 0.20f)),
        )
        Box(
            modifier = Modifier
                .size((64 * scale).dp)
                .clip(CircleShape)
                .background(NotesPalette.Gold.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(NotesPalette.Caramel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "Stop recording",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** A soft status pill, e.g. "Listening..." or an error message. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = NotesPalette.Info,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tone,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
