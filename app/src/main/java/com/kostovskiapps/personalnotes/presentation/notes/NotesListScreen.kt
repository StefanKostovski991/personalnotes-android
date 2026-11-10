package com.kostovskiapps.personalnotes.presentation.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kostovskiapps.personalnotes.domain.model.Note
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.domain.model.NoteSort
import com.kostovskiapps.personalnotes.presentation.components.ColorSwatch
import com.kostovskiapps.personalnotes.presentation.components.NoteCard
import com.kostovskiapps.personalnotes.presentation.components.NotesLogo
import com.kostovskiapps.personalnotes.presentation.components.NotesSearchField
import com.kostovskiapps.personalnotes.presentation.theme.GradientBackground
import com.kostovskiapps.personalnotes.presentation.theme.LocalNotesGradients
import com.kostovskiapps.personalnotes.presentation.theme.NotesPalette

/**
 * The home screen: every stored note, newest first, with search, colour filter and sort.
 */
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    onCreateNote: () -> Unit,
    onCreateSpokenNote: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenSharedFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var notePendingDeletion by remember { mutableStateOf<Note?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    GradientBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExtendedFloatingActionButton(
                        onClick = onCreateSpokenNote,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                        text = { Text("Speak") },
                    )
                    ExtendedFloatingActionButton(
                        onClick = onCreateNote,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("New note") },
                    )
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                NotesHeader(
                    totalCount = state.totalCount,
                    visibleCount = state.notes.size,
                    onOpenSharedFile = onOpenSharedFile,
                    onSortClick = { sortMenuOpen = true },
                    sortMenuOpen = sortMenuOpen,
                    onSortDismiss = { sortMenuOpen = false },
                    currentSort = state.filter.sort,
                    onSortSelected = {
                        viewModel.onSortSelected(it)
                        sortMenuOpen = false
                    },
                )

                NotesSearchField(
                    query = state.filter.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                ColorFilterRow(
                    selected = state.filter.color,
                    onSelect = viewModel::onColorSelected,
                    modifier = Modifier.padding(top = 12.dp),
                )

                when {
                    state.isLoading -> LoadingNotes()

                    state.isEmpty && state.hasActiveFilter -> NoMatches(
                        onClear = viewModel::clearFilters,
                    )

                    state.isEmpty -> EmptyNotes(
                        onCreateNote = onCreateNote,
                        onCreateSpokenNote = onCreateSpokenNote,
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 120.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(
                            items = state.notes,
                            key = { _, note -> note.id },
                        ) { _, note ->
                            var appeared by remember(note.id) { mutableStateOf(false) }
                            LaunchedEffect(note.id) { appeared = true }
                            AnimatedVisibility(
                                visible = appeared,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                            ) {
                                NoteRow(
                                    note = note,
                                    onClick = { onOpenNote(note.id) },
                                    onDelete = { notePendingDeletion = note },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    notePendingDeletion?.let { note ->
        AlertDialog(
            onDismissRequest = { notePendingDeletion = null },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Delete this note?") },
            text = {
                Text("\"${note.displayTitle}\" and its recording will be removed from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(note)
                        notePendingDeletion = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { notePendingDeletion = null }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun NotesHeader(
    totalCount: Int,
    visibleCount: Int,
    onOpenSharedFile: () -> Unit,
    onSortClick: () -> Unit,
    sortMenuOpen: Boolean,
    onSortDismiss: () -> Unit,
    currentSort: NoteSort,
    onSortSelected: (NoteSort) -> Unit,
) {
    val gradients = LocalNotesGradients.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(gradients.signature)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The app mark, so the brand is present on the home screen as well as the icon.
            NotesLogo(
                size = 58.dp,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My notes",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NotesPalette.Ink,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        totalCount == 0 -> "Nothing saved yet"
                        visibleCount != totalCount -> "$visibleCount of $totalCount notes"
                        totalCount == 1 -> "1 note on this device"
                        else -> "$totalCount notes on this device"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NotesPalette.Ink.copy(alpha = 0.75f),
                )
            }

            Box {
                HeaderIconButton(
                    icon = Icons.Filled.FileOpen,
                    description = "Open a shared note file",
                    onClick = onOpenSharedFile,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                HeaderIconButton(
                    icon = Icons.Filled.Sort,
                    description = "Change sorting",
                    onClick = onSortClick,
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = onSortDismiss,
                ) {
                    NoteSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sort.label,
                                    fontWeight = if (sort == currentSort) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = { onSortSelected(sort) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Sort,
                                    contentDescription = null,
                                    tint = if (sort == currentSort) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = NotesPalette.Ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ColorFilterRow(
    selected: NoteColor?,
    onSelect: (NoteColor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        AllColorsChip(
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        NoteColor.entries.forEach { color ->
            ColorSwatch(
                color = color,
                selected = selected == color,
                onClick = { onSelect(if (selected == color) null else color) },
                size = 28,
            )
        }
    }
}

@Composable
private fun AllColorsChip(selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, label = "allColorsScale")
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = "All",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        NoteCard(note = note, onClick = onClick)
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${note.displayTitle}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LoadingNotes() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Opening your notebook...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyNotes(
    onCreateNote: () -> Unit,
    onCreateSpokenNote: () -> Unit,
) {
    val gradients = LocalNotesGradients.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(gradients.signature),
                contentAlignment = Alignment.Center,
            ) {
                NotesLogo(size = 96.dp)
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Your notebook is empty",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Type a note or hold the mic and speak - notes stay on this device until you share them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(
                    onClick = onCreateNote,
                    shape = CircleShape,
                ) { Text("Write a note") }
                androidx.compose.material3.OutlinedButton(
                    onClick = onCreateSpokenNote,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak a note")
                }
            }
        }
    }
}

@Composable
private fun NoMatches(onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No notes match that filter",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClear) { Text("Clear filters") }
        }
    }
}
