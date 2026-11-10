package com.kostovskiapps.personalnotes.presentation.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kostovskiapps.personalnotes.core.DateTimeFormatter
import com.kostovskiapps.personalnotes.data.voice.DictationState
import com.kostovskiapps.personalnotes.data.voice.RecordingState
import com.kostovskiapps.personalnotes.domain.model.NoteColor
import com.kostovskiapps.personalnotes.presentation.components.ColorSwatch
import com.kostovskiapps.personalnotes.presentation.components.MetaChip
import com.kostovskiapps.personalnotes.presentation.components.PulsingMic
import com.kostovskiapps.personalnotes.presentation.components.StatusPill
import com.kostovskiapps.personalnotes.presentation.notes.EditorMode
import com.kostovskiapps.personalnotes.presentation.notes.EditorUiState
import com.kostovskiapps.personalnotes.presentation.notes.EditorViewModel
import com.kostovskiapps.personalnotes.presentation.theme.GradientBackground
import com.kostovskiapps.personalnotes.presentation.theme.LocalNotesGradients
import com.kostovskiapps.personalnotes.presentation.theme.NotesPalette

/**
 * Where a note is written: type it, dictate it, record it, tag the place and pick a colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onShare: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAfterPermission
        pendingAfterPermission = null
        if (granted) action?.invoke() else viewModel.showMessage(AudioPermissionDenied)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) viewModel.captureLocation()
        else viewModel.showMessage(LocationPermissionDenied)
    }

    // Microphone is requested on first use, never at startup.
    val withAudioPermission: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) action() else pendingAfterPermission = action
        if (!granted) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // A saved note is shared straight away: the user pressed "Share", not "Save".
    LaunchedEffect(state.savedId) {
        val id = state.savedId
        if (id != null) {
            viewModel.consumeSaved()
            onShare(id)
        }
    }

    // Back (or the editor being done with the note) always leaves the screen behind.
    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) {
            viewModel.consumeExit()
            onBack()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    GradientBackground(modifier = modifier) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                EditorTopBar(
                    isNew = state.isNew,
                    canSave = state.hasContent,
                    onBack = viewModel::saveAndExit,
                    onSave = viewModel::save,
                    onShare = viewModel::requestShare,
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EditorModeSelector(
                    mode = state.mode,
                    onModeChange = viewModel::onModeChange,
                )

                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    placeholder = { Text("What is this about?") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                    colors = editorFieldColors(),
                )

                when (state.mode) {
                    EditorMode.TYPING -> TypingPanel(
                        description = state.description,
                        hasVoice = state.hasVoice,
                        isRecording = state.isRecording,
                        onDescriptionChange = viewModel::onDescriptionChange,
                        onDictate = { withAudioPermission { viewModel.startDictation() } },
                    )

                    EditorMode.SPEAKING -> SpeakingPanel(
                        description = state.description,
                        dictation = state.dictation,
                        recording = state.recording,
                        onToggleDictation = {
                            withAudioPermission { viewModel.toggleDictation() }
                        },
                        onDescriptionChange = viewModel::onDescriptionChange,
                    )
                }

                VoiceRecordingRow(
                    state = state,
                    onToggleRecord = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            withAudioPermission { viewModel.startRecording() }
                        }
                    },
                    onCancelRecord = viewModel::cancelRecording,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSeek = viewModel::seekPlayback,
                    onRemove = viewModel::removeVoiceRecording,
                )

                MetaPanel(
                    state = state,
                    onCaptureLocation = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onLocationLabelChange = viewModel::onLocationLabelChange,
                    onClearLocation = viewModel::clearLocation,
                )

                ColorPanel(
                    selected = state.color,
                    onSelect = viewModel::onColorChange,
                )

                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

private const val AudioPermissionDenied =
    "Microphone permission is needed for voice notes and dictation"

private const val LocationPermissionDenied =
    "Location permission is needed to tag the place of a note"

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    isNew: Boolean,
    canSave: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val gradients = LocalNotesGradients.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(gradients.signature)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NotesPalette.Ink)
        }
        Text(
            text = if (isNew) "New note" else "Edit note",
            style = MaterialTheme.typography.titleLarge,
            color = NotesPalette.Ink,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onShare, enabled = canSave) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share this note",
                tint = NotesPalette.Ink.copy(alpha = if (canSave) 1f else 0.4f),
            )
        }
        IconButton(onClick = onSave, enabled = canSave) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = "Save note",
                tint = NotesPalette.Ink.copy(alpha = if (canSave) 1f else 0.4f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorModeSelector(mode: EditorMode, onModeChange: (EditorMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        EditorMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = mode == entry,
                onClick = { onModeChange(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, EditorMode.entries.size),
                icon = {
                    Icon(
                        imageVector = if (entry == EditorMode.TYPING) {
                            Icons.Filled.Keyboard
                        } else {
                            Icons.Filled.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            ) {
                Text(if (entry == EditorMode.TYPING) "Type" else "Speak")
            }
        }
    }
}

@Composable
private fun TypingPanel(
    description: String,
    hasVoice: Boolean,
    isRecording: Boolean,
    onDescriptionChange: (String) -> Unit,
    onDictate: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            label = { Text("Note") },
            placeholder = { Text("Write it down...") },
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            colors = editorFieldColors(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDictate, enabled = !isRecording) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Dictate instead")
            }
            if (hasVoice) {
                Spacer(Modifier.weight(1f))
                StatusPill(text = "Has a recording", tone = NotesPalette.Success)
            }
        }
    }
}

@Composable
private fun SpeakingPanel(
    description: String,
    dictation: DictationState,
    recording: RecordingState,
    onToggleDictation: () -> Unit,
    onDescriptionChange: (String) -> Unit,
) {
    val isListening = dictation is DictationState.Listening ||
        (dictation is DictationState.Notice && recording !is RecordingState.Recording)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(LocalNotesGradients.current.card)
            .border(1.dp, NotesPalette.Gold.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Speak your note",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your words appear below and can be edited afterwards.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier.clickable(onClick = onToggleDictation),
            contentAlignment = Alignment.Center,
        ) {
            PulsingMic(
                amplitude = (dictation as? DictationState.Heard)?.text?.length?.let { 0.2f } ?: 0.35f,
            )
        }

        Spacer(Modifier.height(12.dp))
        when (dictation) {
            is DictationState.Listening -> StatusPill("Listening...", tone = NotesPalette.Danger)
            is DictationState.Notice -> StatusPill(dictation.message, tone = NotesPalette.Info)
            is DictationState.Failed -> StatusPill(dictation.reason, tone = NotesPalette.Danger)
            is DictationState.Heard -> StatusPill("Tap to keep listening", tone = NotesPalette.Success)
            DictationState.Idle -> StatusPill("Tap the mic to start", tone = NotesPalette.Info)
        }

        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            label = { Text("Transcribed note") },
            placeholder = { Text("Everything you say lands here") },
            shape = RoundedCornerShape(20.dp),
            colors = editorFieldColors(),
        )

        AnimatedVisibility(visible = isListening) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(CircleShape),
                color = NotesPalette.Amber,
            )
        }
    }
}

@Composable
private fun VoiceRecordingRow(
    state: EditorUiState,
    onToggleRecord: () -> Unit,
    onCancelRecord: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Float) -> Unit,
    onRemove: () -> Unit,
) {
    val recording = state.recording
    val playback = state.playback

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voice note",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        recording is RecordingState.Recording ->
                            "Recording ${DateTimeFormatter.duration(recording.elapsedMs)}"

                        state.hasVoice -> "Saved recording attached"
                        else -> "Record audio for this note"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recording is RecordingState.Recording) {
                IconButton(onClick = onCancelRecord) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard recording")
                }
                IconButton(onClick = onToggleRecord) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop recording",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Button(
                    onClick = onToggleRecord,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.hasVoice) "Re-record" else "Record")
                }
            }
        }

        if (recording is RecordingState.Recording) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { recording.amplitude },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                color = NotesPalette.Amber,
            )
        }

        if (state.hasVoice && recording !is RecordingState.Recording) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = NotesPalette.Caramel,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { playback.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        color = NotesPalette.Caramel,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            text = "${DateTimeFormatter.duration(playback.positionMs)} / " +
                                DateTimeFormatter.duration(playback.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove recording",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Slider(
                value = playback.progress,
                onValueChange = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            playback.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MetaPanel(
    state: EditorUiState,
    onCaptureLocation: () -> Unit,
    onLocationLabelChange: (String) -> Unit,
    onClearLocation: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Where",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.location.label.ifBlank { "No place attached" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.location.label.isNotBlank()) {
                IconButton(onClick = onClearLocation) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove place")
                }
            }
            Button(onClick = onCaptureLocation, enabled = !state.isLocating, shape = CircleShape) {
                Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.isLocating) "Locating..." else "Use my location")
            }
        }

        OutlinedTextField(
            value = state.location.label,
            onValueChange = onLocationLabelChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Or type a place") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = editorFieldColors(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaChip(
                icon = Icons.Filled.Check,
                text = DateTimeFormatter.fullTimestamp(
                    state.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                ),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorPanel(selected: NoteColor, onSelect: (NoteColor) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(16.dp),
    ) {
        Text(
            text = "Colour tag",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoteColor.entries.forEach { color ->
                ColorSwatch(
                    color = color,
                    selected = selected == color,
                    onClick = { onSelect(color) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = selected.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
